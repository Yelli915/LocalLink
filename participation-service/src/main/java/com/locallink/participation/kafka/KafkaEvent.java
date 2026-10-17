package com.locallink.participation.kafka;

import lombok.*;

/**
 * Kafka 이벤트 메시지 DTO
 */
public class KafkaEvent {

    /**
     * Settlement Service → Participation Service
     * 정산 완료 이벤트 수신
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SettlementCompletedEvent {
        private Long settlementId;
        private Long userId;
        private Long programId;
        private String status; // COMPLETED
    }

    /**
     * Participation Service → Recommend Service
     * 참여 활성화 완료 이벤트 발행
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ParticipationCompletedEvent {
        private Long participationId;
        private Long userId;
        private Long programId;
    }
}
