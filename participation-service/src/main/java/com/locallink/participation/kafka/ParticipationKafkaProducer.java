package com.locallink.participation.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ParticipationKafkaProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topic.participation-completed}")
    private String participationCompletedTopic;

    /**
     * participation.completed 이벤트 발행
     * → Recommend Service가 수신하여 추천 갱신
     */
    public void publishParticipationCompleted(KafkaEvent.ParticipationCompletedEvent event) {
        log.info("[Kafka Producer] participation.completed 발행 - participationId: {}, userId: {}, programId: {}",
                event.getParticipationId(), event.getUserId(), event.getProgramId());

        kafkaTemplate.send(participationCompletedTopic, String.valueOf(event.getUserId()), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[Kafka Producer] participation.completed 발행 실패: {}", ex.getMessage());
                    } else {
                        log.info("[Kafka Producer] participation.completed 발행 성공 - offset: {}",
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
