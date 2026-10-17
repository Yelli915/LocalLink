package com.locallink.participation;

import com.locallink.participation.entity.Participation;
import com.locallink.participation.kafka.ParticipationKafkaProducer;
import com.locallink.participation.repository.ParticipationRepository;
import com.locallink.participation.service.ProgramServiceClient;
import com.locallink.participation.service.ParticipationWriteService;
import com.locallink.participation.service.SettlementServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 정산 서비스 장애 → FAILED 보상 처리 → 복구 후 재신청 성공 시나리오.
 * DB 는 H2, 외부 서비스(program/settlement)와 Kafka 는 목/비활성화.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:participation;MODE=MariaDB;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "eureka.client.enabled=false",
        "spring.kafka.listener.auto-startup=false",
        "participation.cleanup-interval-ms=3600000"
})
@AutoConfigureMockMvc
class ParticipationRetryIntegrationTest {

    private static final long USER_ID = 1L;
    private static final long PROGRAM_ID = 10L;
    private static final String BODY = "{\"programId\": " + PROGRAM_ID + "}";

    @Autowired MockMvc mockMvc;
    @Autowired ParticipationRepository participationRepository;
    @Autowired ParticipationWriteService participationWriteService;

    @MockitoBean ProgramServiceClient programServiceClient;
    @MockitoBean SettlementServiceClient settlementServiceClient;
    @MockitoBean ParticipationKafkaProducer kafkaProducer;

    @BeforeEach
    void setUp() {
        participationRepository.deleteAll();
        reset(programServiceClient, settlementServiceClient);
        when(programServiceClient.getProgramPrice(PROGRAM_ID)).thenReturn(BigDecimal.valueOf(99000));
    }

    private org.springframework.test.web.servlet.ResultActions participate() throws Exception {
        return mockMvc.perform(post("/api/participations")
                .header("X-User-Id", USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY));
    }

    @Test
    void 정산서비스가_내려가면_FAILED가_되고_복구후_재신청이_성공한다() throws Exception {
        // 정산 서비스 다운
        when(settlementServiceClient.requestSettlement(any(), any(), any()))
                .thenThrow(new RuntimeException("Settlement Service 연결 실패"));

        participate().andExpect(status().is5xxServerError());

        Participation failed = participationRepository.findByUserIdAndProgramId(USER_ID, PROGRAM_ID).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(Participation.Status.FAILED);

        // 정산 서비스 복구
        reset(settlementServiceClient);
        when(settlementServiceClient.requestSettlement(any(), any(), any()))
                .thenReturn(new SettlementServiceClient.SettlementResult());

        participate().andExpect(status().isCreated());

        // 새 행이 아니라 기존 행이 PENDING 으로 복구되어야 함 (유니크 제약)
        assertThat(participationRepository.count()).isEqualTo(1);
        Participation retried = participationRepository.findByUserIdAndProgramId(USER_ID, PROGRAM_ID).orElseThrow();
        assertThat(retried.getId()).isEqualTo(failed.getId());
        assertThat(retried.getStatus()).isEqualTo(Participation.Status.PENDING);
    }

    @Test
    void PENDING_상태에서는_중복신청이_거절된다() throws Exception {
        when(settlementServiceClient.requestSettlement(any(), any(), any()))
                .thenReturn(new SettlementServiceClient.SettlementResult());

        participate().andExpect(status().isCreated());
        participate().andExpect(status().isBadRequest());
    }

    @Test
    void 오래된_PENDING은_정리되어_재신청할_수_있다() throws Exception {
        participationWriteService.createPendingParticipation(USER_ID, PROGRAM_ID);

        // 아직 오래되지 않았으면 유지
        assertThat(participationWriteService.failStalePending(LocalDateTime.now().minusMinutes(10))).isZero();
        // 임계 시각이 미래라면 정리 대상
        assertThat(participationWriteService.failStalePending(LocalDateTime.now().plusMinutes(1))).isEqualTo(1);

        assertThat(participationRepository.findByUserIdAndProgramId(USER_ID, PROGRAM_ID).orElseThrow().getStatus())
                .isEqualTo(Participation.Status.FAILED);

        when(settlementServiceClient.requestSettlement(any(), any(), any()))
                .thenReturn(new SettlementServiceClient.SettlementResult());
        participate().andExpect(status().isCreated());
    }
}
