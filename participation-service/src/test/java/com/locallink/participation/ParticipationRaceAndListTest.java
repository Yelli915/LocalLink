package com.locallink.participation;

import com.locallink.participation.dto.ParticipationDto;
import com.locallink.participation.entity.Participation;
import com.locallink.participation.kafka.ParticipationKafkaProducer;
import com.locallink.participation.repository.ParticipationRepository;
import com.locallink.participation.service.ProgramServiceClient;
import com.locallink.participation.service.ParticipationService;
import com.locallink.participation.service.ParticipationWriteService;
import com.locallink.participation.service.SettlementServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * - FAILED 이후 settlement.completed 도착(경합) 시 정산 사실 기준으로 ACTIVE 복구
 * - updatedAt 갱신 / NULL 인 기존 행의 stale 정리
 * - 내 참여 목록: 원본 category 전달, 일부 프로그램 조회 실패에도 목록 유지, 같은 프로그램 1회 조회
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:racelist;MODE=MariaDB;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "eureka.client.enabled=false",
        "spring.kafka.listener.auto-startup=false",
        "participation.cleanup-interval-ms=3600000"
})
class ParticipationRaceAndListTest {

    private static final long USER_ID = 1L;

    @Autowired ParticipationRepository participationRepository;
    @Autowired ParticipationService participationService;
    @Autowired ParticipationWriteService participationWriteService;
    @Autowired JdbcTemplate jdbc;

    @MockitoBean ProgramServiceClient programServiceClient;
    @MockitoBean SettlementServiceClient settlementServiceClient;
    @MockitoBean ParticipationKafkaProducer kafkaProducer;

    @BeforeEach
    void setUp() {
        participationRepository.deleteAll();
        reset(programServiceClient, kafkaProducer);
    }

    @Test
    void FAILED_이후_정산완료_이벤트가_오면_ACTIVE로_복구된다() {
        Participation e = participationWriteService.createPendingParticipation(USER_ID, 10L);
        participationWriteService.markFailed(e.getId());

        participationService.activateParticipation(USER_ID, 10L);

        assertThat(participationRepository.findById(e.getId()).orElseThrow().getStatus())
                .isEqualTo(Participation.Status.ACTIVE);
        verify(programServiceClient, times(1)).increaseParticipationCount(10L);
        verify(kafkaProducer, times(1)).publishParticipationCompleted(any());
    }

    @Test
    void CANCELLED_건은_정산완료_이벤트로_활성화되지_않는다() {
        Participation e = participationRepository.save(Participation.builder().userId(USER_ID).programId(10L).build());
        e.cancel();
        participationRepository.save(e);

        participationService.activateParticipation(USER_ID, 10L);

        assertThat(participationRepository.findById(e.getId()).orElseThrow().getStatus())
                .isEqualTo(Participation.Status.CANCELLED);
        verify(programServiceClient, times(0)).increaseParticipationCount(10L);
    }

    @Test
    void 재신청하면_updatedAt이_갱신된다() throws Exception {
        Participation e = participationWriteService.createPendingParticipation(USER_ID, 10L);
        participationWriteService.markFailed(e.getId());
        jdbc.update("update participations set updated_at = ? where id = ?",
                LocalDateTime.now().minusHours(1), e.getId());

        participationWriteService.createPendingParticipation(USER_ID, 10L);

        Participation retried = participationRepository.findById(e.getId()).orElseThrow();
        assertThat(retried.getStatus()).isEqualTo(Participation.Status.PENDING);
        assertThat(retried.getUpdatedAt()).isAfter(LocalDateTime.now().minusMinutes(1));
        // 방금 재신청한 건은 stale 이 아니다
        assertThat(participationWriteService.failStalePending(LocalDateTime.now().minusMinutes(10))).isZero();
    }

    @Test
    void updatedAt이_NULL인_기존_행도_createdAt_기준으로_정리된다() {
        Participation e = participationWriteService.createPendingParticipation(USER_ID, 10L);
        jdbc.update("update participations set updated_at = null, created_at = ? where id = ?",
                LocalDateTime.now().minusHours(1), e.getId());

        assertThat(participationWriteService.failStalePending(LocalDateTime.now().minusMinutes(10))).isEqualTo(1);
        assertThat(participationRepository.findById(e.getId()).orElseThrow().getStatus())
                .isEqualTo(Participation.Status.FAILED);
    }

    @Test
    void 내_참여목록은_원본_category를_주고_일부_조회_실패에도_목록을_유지한다() {
        participationRepository.save(Participation.builder().userId(USER_ID).programId(10L).build());
        participationRepository.save(Participation.builder().userId(USER_ID).programId(11L).build());
        when(programServiceClient.getProgram(10L))
                .thenReturn(Map.of("id", 10, "title", "A", "category", "SAME_DAY", "price", 1000));
        when(programServiceClient.getProgram(11L)).thenThrow(new RuntimeException("Program Service 프로그램 상세 조회 실패"));

        List<ParticipationDto.ParticipationResponse> list = participationService.getParticipationsByUser(USER_ID);

        assertThat(list).hasSize(2);
        ParticipationDto.ParticipationResponse ok = list.stream().filter(r -> r.getProgramId() == 10L).findFirst().orElseThrow();
        ParticipationDto.ParticipationResponse failed = list.stream().filter(r -> r.getProgramId() == 11L).findFirst().orElseThrow();
        assertThat(ok.getProgram().getCategory()).isEqualTo("SAME_DAY");
        assertThat(failed.getProgram()).isNull();
    }
}
