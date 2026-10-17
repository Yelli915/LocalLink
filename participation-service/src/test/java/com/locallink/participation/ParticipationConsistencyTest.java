package com.locallink.participation;

import com.locallink.participation.dto.ParticipationDto;
import com.locallink.participation.entity.Participation;
import com.locallink.participation.kafka.ParticipationKafkaProducer;
import com.locallink.participation.repository.ParticipationRepository;
import com.locallink.participation.service.ProgramServiceClient;
import com.locallink.participation.service.ParticipationService;
import com.locallink.participation.service.ParticipationWriteService;
import com.locallink.participation.service.SettlementOutcomeUnknownException;
import com.locallink.participation.service.SettlementServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 상태 전이의 원자성 / 정산 결과 불명 처리 / 목록 조회 방어 로직 검증.
 * DB 는 H2, 외부 서비스와 Kafka 는 목.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:consistency;MODE=MariaDB;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "eureka.client.enabled=false",
        "spring.kafka.listener.auto-startup=false",
        "participation.cleanup-interval-ms=3600000",
        "participation.program-lookup-timeout-ms=300"
})
class ParticipationConsistencyTest {

    private static final long USER_ID = 1L;
    private static final long PROGRAM_ID = 10L;

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
        reset(programServiceClient, settlementServiceClient, kafkaProducer);
        when(programServiceClient.getProgramPrice(PROGRAM_ID)).thenReturn(BigDecimal.valueOf(99000));
    }

    private Participation.Status statusOf(long programId) {
        return participationRepository.findByUserIdAndProgramId(USER_ID, programId).orElseThrow().getStatus();
    }

    @Test
    void 정산_결과를_알_수_없으면_FAILED로_단정하지_않고_PENDING을_유지한다() {
        when(settlementServiceClient.requestSettlement(any(), any(), any()))
                .thenThrow(new SettlementOutcomeUnknownException("응답 대기 시간 초과", null));

        ParticipationDto.ParticipationResponse response = participationService.participate(USER_ID, PROGRAM_ID);

        assertThat(response.getStatus()).isEqualTo(Participation.Status.PENDING);
        assertThat(statusOf(PROGRAM_ID)).isEqualTo(Participation.Status.PENDING);

        // 늦게라도 정산 완료 이벤트가 오면 정상적으로 ACTIVE 가 된다
        participationService.activateParticipation(USER_ID, PROGRAM_ID);
        assertThat(statusOf(PROGRAM_ID)).isEqualTo(Participation.Status.ACTIVE);
    }

    @Test
    void 정산_요청이_확실히_실패하면_FAILED로_보상된다() {
        when(settlementServiceClient.requestSettlement(any(), any(), any()))
                .thenThrow(new RuntimeException("Settlement Service 연결 실패"));

        assertThatThrownBy(() -> participationService.participate(USER_ID, PROGRAM_ID))
                .isInstanceOf(RuntimeException.class);

        assertThat(statusOf(PROGRAM_ID)).isEqualTo(Participation.Status.FAILED);
    }

    @Test
    void 같은_정산완료_이벤트를_동시에_처리해도_부수효과는_한_번만_일어난다() throws Exception {
        participationWriteService.createPendingParticipation(USER_ID, PROGRAM_ID);

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                participationService.activateParticipation(USER_ID, PROGRAM_ID);
                return null;
            }));
        }
        ready.await();
        go.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        assertThat(statusOf(PROGRAM_ID)).isEqualTo(Participation.Status.ACTIVE);
        verify(programServiceClient, times(1)).increaseParticipationCount(PROGRAM_ID);
        verify(kafkaProducer, times(1)).publishParticipationCompleted(any());
    }

    @Test
    void 정리_작업과_보상_처리는_이미_ACTIVE인_건을_덮어쓰지_않는다() {
        Participation e = participationWriteService.createPendingParticipation(USER_ID, PROGRAM_ID);
        participationWriteService.activate(USER_ID, PROGRAM_ID);

        // 임계 시각이 미래여도 PENDING 이 아니면 정리 대상이 아니다
        assertThat(participationWriteService.failStalePending(LocalDateTime.now().plusMinutes(1))).isZero();
        participationWriteService.markFailed(e.getId());

        assertThat(statusOf(PROGRAM_ID)).isEqualTo(Participation.Status.ACTIVE);
    }

    @Test
    void 이미_진행중이거나_완료된_신청은_PENDING으로_되돌릴_수_없다() {
        participationWriteService.createPendingParticipation(USER_ID, PROGRAM_ID);
        participationWriteService.activate(USER_ID, PROGRAM_ID);

        assertThatThrownBy(() -> participationWriteService.createPendingParticipation(USER_ID, PROGRAM_ID))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(statusOf(PROGRAM_ID)).isEqualTo(Participation.Status.ACTIVE);
    }

    @Test
    void 느린_프로그램_조회는_타임아웃으로_해당_항목만_비우고_목록은_반환한다() {
        participationRepository.save(Participation.builder().userId(USER_ID).programId(10L).build());
        participationRepository.save(Participation.builder().userId(USER_ID).programId(11L).build());
        when(programServiceClient.getProgram(10L)).thenReturn(Map.of("id", 10, "title", "A"));
        when(programServiceClient.getProgram(11L)).thenAnswer(invocation -> {
            Thread.sleep(3_000);
            return Map.of("id", 11, "title", "B");
        });

        long start = System.currentTimeMillis();
        List<ParticipationDto.ParticipationResponse> list = participationService.getParticipationsByUser(USER_ID);
        long elapsed = System.currentTimeMillis() - start;

        assertThat(elapsed).isLessThan(2_500);
        assertThat(list).hasSize(2);
        assertThat(list.stream().filter(r -> r.getProgramId() == 10L).findFirst().orElseThrow().getProgram()).isNotNull();
        assertThat(list.stream().filter(r -> r.getProgramId() == 11L).findFirst().orElseThrow().getProgram()).isNull();
    }

    @Test
    void 프로그램_응답의_필드명_변형과_추가_필드를_흡수한다() {
        participationRepository.save(Participation.builder().userId(USER_ID).programId(10L).build());
        when(programServiceClient.getProgram(10L)).thenReturn(Map.of(
                "id", 10,
                "title", "A",
                "price", 99000.0,                // 소수점 표기여도 정수로
                "officer_name", "김담당",         // officerName 의 변형
                "participation_count", 7,           // participationCount 의 변형
                "someNewField", "ignored"));     // 모르는 필드는 무시

        ParticipationDto.ProgramSummary program = participationService.getParticipationsByUser(USER_ID)
                .get(0).getProgram();

        assertThat(program).isNotNull();
        assertThat(program.getPrice()).isEqualTo(99000);
        assertThat(program.getOfficerName()).isEqualTo("김담당");
        assertThat(program.getParticipationCount()).isEqualTo(7);
    }
}
