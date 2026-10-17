package com.locallink.participation.kafka;

import com.locallink.participation.service.ParticipationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ParticipationKafkaConsumer {

    private final ParticipationService participationService;

    /**
     * settlement.completed 이벤트 수신
     * → 참여 상태 PENDING → ACTIVE 로 변경
     * → participation.completed 이벤트 발행 (→ Recommend Service)
     *
     * settlement-service 쪽은 JsonSerializer + type header 미포함으로 이벤트를 발행하므로,
     * 여기서는 특정 DTO 타입으로 바로 받지 않고 Map<String, Object> 로 받아 처리한다.
     */
    @KafkaListener(
            topics = "${kafka.topic.settlement-completed}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleSettlementCompleted(Map<String, Object> event) {
        log.info("[Kafka Consumer] settlement.completed raw event 수신: {}", event);

        try {
            Object userIdValue = event.get("userId");
            Object programIdValue = event.get("programId");

            if (userIdValue == null || programIdValue == null) {
                throw new IllegalArgumentException("Kafka 이벤트에 userId 또는 programId가 없습니다.");
            }

            Long userId = ((Number) userIdValue).longValue();
            Long programId = ((Number) programIdValue).longValue();

            log.info("[Kafka Consumer] settlement.completed 파싱 완료 - userId: {}, programId: {}",
                    userId, programId);

            participationService.activateParticipation(userId, programId);

            log.info("[Kafka Consumer] 참여 활성화 완료 - userId: {}, programId: {}",
                    userId, programId);

        } catch (Exception e) {
            log.error("[Kafka Consumer] 참여 활성화 실패 - event: {}, error: {}",
                    event, e.getMessage(), e);
        }
    }
}