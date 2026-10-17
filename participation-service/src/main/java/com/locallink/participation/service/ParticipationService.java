package com.locallink.participation.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.locallink.participation.dto.ParticipationDto;
import com.locallink.participation.entity.Participation;
import com.locallink.participation.kafka.ParticipationKafkaProducer;
import com.locallink.participation.kafka.KafkaEvent;
import com.locallink.participation.repository.ParticipationRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 이 클래스에는 클래스 레벨 @Transactional 이 없다.
 * REST/Kafka 같은 외부 호출을 DB 트랜잭션(=커넥션 점유) 안에서 하지 않기 위해서이며,
 * 상태를 바꾸는 짧은 트랜잭션은 {@link ParticipationWriteService} 가 전담한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParticipationService {

    private static final String ALREADY_PARTICIPATED = "이미 참여신청한 프로그램입니다";

    // 모르는 필드는 무시 (program-service 응답에 필드가 늘어나도 목록이 깨지지 않게)
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final ParticipationRepository participationRepository;
    private final ProgramServiceClient programServiceClient;
    private final SettlementServiceClient settlementServiceClient;
    private final ParticipationKafkaProducer kafkaProducer;
    private final ParticipationWriteService participationWriteService;

    /** 한 항목의 프로그램 상세 조회에 허용하는 최대 시간. 초과하면 그 항목의 program 만 비운다. */
    @Value("${participation.program-lookup-timeout-ms:3000}")
    private long programLookupTimeoutMs;

    /**
     * "내 참여 목록" 의 프로그램 상세 병렬 조회 전용 풀.
     * 공용 ForkJoinPool 을 쓰면 느린 program-service 호출이 다른 작업까지 막으므로 분리하고,
     * 큐가 차면 호출 스레드가 직접 실행(CallerRuns)해 요청을 버리지 않으면서 속도를 조절한다.
     */
    private final ExecutorService programLookupExecutor = new ThreadPoolExecutor(
            4, 8, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(100),
            runnable -> {
                Thread t = new Thread(runnable, "program-lookup");
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.CallerRunsPolicy());

    @PreDestroy
    void shutdownExecutor() {
        programLookupExecutor.shutdown();
    }

    /**
     * 참여신청 전체 흐름
     * 1. 가격 조회 (프로그램 존재 확인 + 가격 확정) - 검증은 상태를 남기기 전에 모두 끝낸다
     * 2. 중복 참여 확인 (PENDING/ACTIVE 만 중복, FAILED 는 재신청 허용)
     * 3. Participation 생성(또는 FAILED 행 복구) 및 즉시 커밋 (PENDING)
     * 4. 정산 요청
     *    - 확실한 실패(연결 거부/4xx/FAILED 응답): PENDING → FAILED 보상 처리 후 예외 재전파
     *    - 결과를 알 수 없음(타임아웃/5xx): 정산이 실제로 됐을 수 있으므로 FAILED 로 단정하지 않고
     *      PENDING 으로 둔다. settlement.completed 가 오면 ACTIVE, 끝내 안 오면 정리 작업이 FAILED 로 바꾼다.
     *      (사용자에게 FAILED 가 잠깐 보였다가 ACTIVE 로 뒤집히는 일을 막는다)
     */
    public ParticipationDto.ParticipationResponse participate(Long userId, Long programId) {
        BigDecimal programPrice = programServiceClient.getProgramPrice(programId);

        if (participationRepository.existsByUserIdAndProgramIdAndStatusIn(
                userId, programId, List.of(Participation.Status.PENDING, Participation.Status.ACTIVE))) {
            throw new IllegalArgumentException(ALREADY_PARTICIPATED);
        }

        Participation participation;
        try {
            participation = participationWriteService.createPendingParticipation(userId, programId);
        } catch (DataIntegrityViolationException e) {
            // 위 존재 확인과 INSERT 사이에 같은 신청이 먼저 들어온 경우 (유니크 제약 위반)
            throw new IllegalArgumentException(ALREADY_PARTICIPATED);
        }

        try {
            SettlementServiceClient.SettlementResult result =
                    settlementServiceClient.requestSettlement(userId, programId, programPrice);
            if (result != null && "FAILED".equals(result.getStatus())) {
                throw new RuntimeException("정산이 실패했습니다");
            }
        } catch (SettlementOutcomeUnknownException e) {
            log.warn("[ParticipationService] 정산 결과 불명 - PENDING 유지, 이벤트/정리 작업으로 확정 - participationId: {}, error: {}",
                    participation.getId(), e.getMessage());
        } catch (RuntimeException e) {
            participationWriteService.markFailed(participation.getId());
            throw e;
        }

        log.info("[ParticipationService] 참여신청 완료 (정산 대기) - participationId: {}", participation.getId());
        return ParticipationDto.ParticipationResponse.from(participation);
    }

    /**
     * 참여 활성화 (멱등: 이미 ACTIVE 면 아무것도 하지 않음)
     *
     * 순서: ① DB 상태 전이를 먼저 커밋 → ② 커밋이 끝난 뒤에만 외부 부수효과(참여 카운트 증가, 이벤트 발행).
     * 부수효과를 트랜잭션 안에서 하면 "REST 는 성공했는데 커밋이 실패 → 이벤트 재전달 → 카운트 중복 증가" 가
     * 생길 수 있어서, 중복(at-least-once)보다 유실(at-most-once)을 택했다.
     *
     * 알려진 한계: ①과 ② 사이에 프로세스가 죽으면 카운트/이벤트가 유실된다. 이 프로젝트는 학습·시연 목적이라 감수하지만,
     * 운영 수준이라면 outbox 테이블에 같은 트랜잭션으로 기록하고 별도 릴레이가 발행하는 방식으로 바꿔야 한다.
     */
    public void activateParticipation(Long userId, Long programId) {
        // 조건부 UPDATE 로 실제 전이시킨 호출만 값을 받는다 → 중복/동시 이벤트에서도 부수효과는 1회
        Participation participation = participationWriteService.activate(userId, programId).orElse(null);
        if (participation == null) {
            return;
        }

        programServiceClient.increaseParticipationCount(programId);

        kafkaProducer.publishParticipationCompleted(
                KafkaEvent.ParticipationCompletedEvent.builder()
                        .participationId(participation.getId())
                        .userId(userId)
                        .programId(programId)
                        .build());

        log.info("[ParticipationService] 참여 활성화 완료 - participationId: {}", participation.getId());
    }

    /**
     * 사용자 참여 목록 조회
     * - program-service에서 프로그램 상세 정보를 붙여서 반환
     */
    public List<ParticipationDto.ParticipationResponse> getParticipationsByUser(Long userId) {
        List<Participation> participations = participationRepository.findByUserId(userId);

        // 같은 프로그램은 한 번만, 서로 다른 프로그램은 전용 풀에서 병렬로 조회한다.
        // 일부 조회가 실패/지연돼도 목록 전체를 실패시키지 않고 해당 항목의 program 만 비운다(null).
        Map<Long, CompletableFuture<ParticipationDto.ProgramSummary>> summaries = participations.stream()
                .map(Participation::getProgramId)
                .distinct()
                .collect(Collectors.toMap(
                        programId -> programId,
                        programId -> CompletableFuture
                                .supplyAsync(() -> fetchProgramSummary(programId), programLookupExecutor)
                                .completeOnTimeout(null, programLookupTimeoutMs, TimeUnit.MILLISECONDS)));

        return participations.stream()
                .map(participation -> ParticipationDto.ParticipationResponse.from(
                        participation, summaries.get(participation.getProgramId()).join()))
                .collect(Collectors.toList());
    }

    private ParticipationDto.ProgramSummary fetchProgramSummary(Long programId) {
        try {
            Map<String, Object> programInfo = programServiceClient.getProgram(programId);
            // program-service 의 원본 enum 을 그대로 전달한다 (표시용 라벨 변환은 프론트 책임)
            return MAPPER.convertValue(programInfo, ParticipationDto.ProgramSummary.class);
        } catch (RuntimeException e) {
            log.warn("[ParticipationService] 프로그램 상세 조회 실패 - 요약 없이 반환 - programId: {}, error: {}",
                    programId, e.getMessage());
            return null;
        }
    }

    /**
     * 참여 이력 조회 - 추천 서비스용
     */
    public ParticipationDto.ParticipationHistoryResponse getParticipationHistory(Long userId) {
        List<Long> activeProgramIds = participationRepository
                .findByUserIdAndStatus(userId, Participation.Status.ACTIVE)
                .stream()
                .map(Participation::getProgramId)
                .collect(Collectors.toList());

        return ParticipationDto.ParticipationHistoryResponse.builder()
                .userId(userId)
                .activeProgramIds(activeProgramIds)
                .build();
    }
}
