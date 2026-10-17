package com.locallink.participation.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Value("${kafka.topic.settlement-completed}")
    private String settlementCompletedTopic;

    @Value("${kafka.topic.participation-completed}")
    private String participationCompletedTopic;

    @Bean
    public NewTopic settlementCompletedTopic() {
        return TopicBuilder.name(settlementCompletedTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic participationCompletedTopic() {
        return TopicBuilder.name(participationCompletedTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
