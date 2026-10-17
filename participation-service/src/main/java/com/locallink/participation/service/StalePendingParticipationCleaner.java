package com.locallink.participation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 일정 시간 이상 PENDING 으로 남은 신청을 FAILED 로 정리해 재신청이 가능하도록 한다.
 * (settlement.completed 이벤트 유실 / 보상 처리 실패 / 정산 결과 불명 대비)
 *
 * 인스턴스가 여러 개여도 안전하다: 정리는 "status=PENDING 이고 오래된 행" 을 한 문장으로 갱신하는
 * 조건부 UPDATE 라서 중복 실행돼도 결과가 같고, 그 사이 ACTIVE 가 된 건은 덮어쓰지 않는다.
 * (스케줄러는 JpaConfig 의 @EnableScheduling 으로 활성화된다)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StalePendingParticipationCleaner {

    private final ParticipationWriteService participationWriteService;

    @Value("${participation.pending-timeout-minutes:10}")
    private long pendingTimeoutMinutes;

    @Scheduled(fixedDelayString = "${participation.cleanup-interval-ms:60000}")
    public void cleanUp() {
        int count = participationWriteService.failStalePending(
                LocalDateTime.now().minusMinutes(pendingTimeoutMinutes));
        if (count > 0) {
            log.warn("[StalePendingParticipationCleaner] 오래된 PENDING {}건을 FAILED 처리", count);
        }
    }
}
