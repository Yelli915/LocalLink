package com.locallink.settlement.kafka;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementKafkaProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topic.settlement-completed}")
    private String settlementCompletedTopic;

    /**
     * settlement.completed 이벤트 발행
     * → Participation Service가 수신하여 참여 활성화
     *
     * 개발/검증 단계에서는 전송 성공 여부를 즉시 확인하기 위해 동기적으로 기다린다.
     */
    public void publishSettlementCompleted(SettlementCompletedEvent event) {
        log.info("[Kafka Producer] settlement.completed 발행 시도 - topic: {}, settlementId: {}, userId: {}, programId: {}",
                settlementCompletedTopic, event.getSettlementId(), event.getUserId(), event.getProgramId());

        try {
            SendResult<String, Object> result = kafkaTemplate
                    .send(settlementCompletedTopic, String.valueOf(event.getUserId()), event)
                    .get(10, TimeUnit.SECONDS);

            log.info("[Kafka Producer] settlement.completed 발행 성공 - topic: {}, partition: {}, offset: {}",
                    settlementCompletedTopic,
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());

        } catch (Exception e) {
            log.error("[Kafka Producer] settlement.completed 발행 실패 - topic: {}, settlementId: {}, userId: {}, programId: {}, error: {}",
                    settlementCompletedTopic,
                    event.getSettlementId(),
                    event.getUserId(),
                    event.getProgramId(),
                    e.getMessage(),
                    e);

            throw new RuntimeException("settlement.completed Kafka 발행 실패", e);
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SettlementCompletedEvent {
        private Long settlementId;
        private Long userId;
        private Long programId;
        private String status;
    }
}