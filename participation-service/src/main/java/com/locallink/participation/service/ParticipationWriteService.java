package com.locallink.participation.service;

import com.locallink.participation.entity.Participation;
import com.locallink.participation.repository.ParticipationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 상태를 바꾸는 짧은 DB 트랜잭션만 모아 둔 서비스.
 * 외부 호출(REST/Kafka)은 절대 여기서 하지 않는다 - 호출자가 커밋 이후에 수행한다.
 * 모든 상태 전이는 "현재 상태 조건부 UPDATE" 라서 동시에 실행돼도 한 쪽만 성공한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParticipationWriteService {

    private static final String ALREADY_PARTICIPATED = "이미 참여신청한 프로그램입니다";

    private final ParticipationRepository participationRepository;

    /**
     * 반드시 독립 트랜잭션으로 실행
     * - (user_id, program_id) 유니크 제약이 있으므로, 이전에 FAILED/CANCELLED 된 행이 있으면
     *   새 행을 만들지 않고 그 행을 PENDING 으로 되돌린다 (upsert).
     * - 되돌리기는 조건부 UPDATE 라서, 동시에 두 요청이 와도 한 쪽만 성공하고
     *   이미 PENDING/ACTIVE 인 행을 PENDING 으로 덮어쓰는 일이 없다.
     *
     * @throws IllegalArgumentException 이미 PENDING/ACTIVE 인 신청이 있을 때
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Participation createPendingParticipation(Long userId, Long programId) {
        Optional<Participation> existing = participationRepository.findByUserIdAndProgramId(userId, programId);

        Participation participation;
        if (existing.isPresent()) {
            Long id = existing.get().getId();
            int revived = participationRepository.transitionFromAny(
                    id,
                    List.of(Participation.Status.FAILED, Participation.Status.CANCELLED),
                    Participation.Status.PENDING,
                    LocalDateTime.now());
            if (revived == 0) {
                throw new IllegalArgumentException(ALREADY_PARTICIPATED);
            }
            participation = participationRepository.findById(id).orElseThrow();
        } else {
            participation = participationRepository.save(Participation.builder()
                    .userId(userId)
                    .programId(programId)
                    .build());
        }

        log.info("[ParticipationWriteService] PENDING participation 생성/복구 완료 - participationId: {}, userId: {}, programId: {}",
                participation.getId(), userId, programId);

        return participation;
    }

    /**
     * 보상 처리: 정산 요청 실패 시 PENDING → FAILED (독립 트랜잭션)
     * 이미 ACTIVE 가 된 건은 건드리지 않는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long participationId) {
        int updated = participationRepository.transition(
                participationId, Participation.Status.PENDING, Participation.Status.FAILED, LocalDateTime.now());
        if (updated > 0) {
            log.warn("[ParticipationWriteService] participation FAILED 처리 - participationId: {}", participationId);
        }
    }

    /**
     * 정산 완료 반영: PENDING/FAILED → ACTIVE.
     * FAILED 인 경우는 응답 유실/타임아웃으로 보상 처리가 먼저 일어났지만 정산은 실제로 완료된 것이므로
     * 정산 사실을 기준으로 복구한다. ACTIVE(중복 이벤트)/CANCELLED(환불 확인 필요)는 활성화하지 않는다.
     *
     * @return 이번 호출이 실제로 ACTIVE 로 전이시킨 경우에만 해당 신청, 아니면 empty
     *         (동시에 같은 이벤트를 두 번 처리해도 한 쪽만 값을 받는다 → 부수효과 1회 보장)
     * @throws IllegalArgumentException 신청 정보가 없을 때
     */
    @Transactional
    public Optional<Participation> activate(Long userId, Long programId) {
        Participation participation = participationRepository.findByUserIdAndProgramId(userId, programId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "참여 정보를 찾을 수 없습니다 - userId: " + userId + ", programId: " + programId));

        Participation.Status before = participation.getStatus();
        int updated = participationRepository.transitionFromAny(
                participation.getId(),
                List.of(Participation.Status.PENDING, Participation.Status.FAILED),
                Participation.Status.ACTIVE,
                LocalDateTime.now());

        if (updated == 0) {
            if (before == Participation.Status.CANCELLED) {
                log.warn("[ParticipationWriteService] CANCELLED 건에 settlement.completed 도착 - 활성화하지 않음 (환불 확인 필요) - participationId: {}",
                        participation.getId());
            } else {
                log.info("[ParticipationWriteService] 이미 ACTIVE - 중복 이벤트 무시 - participationId: {}", participation.getId());
            }
            return Optional.empty();
        }
        if (before == Participation.Status.FAILED) {
            log.warn("[ParticipationWriteService] FAILED 건에 settlement.completed 도착 - 정산 완료 기준으로 복구 - participationId: {}",
                    participation.getId());
        }
        return participationRepository.findById(participation.getId());
    }

    /**
     * 안전장치: threshold 이전부터 PENDING 인 행을 FAILED 로 정리 (이벤트 유실 대비)
     * 단일 조건부 UPDATE 이므로 여러 인스턴스가 동시에 돌려도, 그 사이 ACTIVE 가 된 건을 덮어쓰지 않는다.
     */
    @Transactional
    public int failStalePending(LocalDateTime threshold) {
        return participationRepository.transitionStale(
                Participation.Status.PENDING, Participation.Status.FAILED, threshold, LocalDateTime.now());
    }
}
