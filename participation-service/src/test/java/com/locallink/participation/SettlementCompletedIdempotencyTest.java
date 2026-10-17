package com.locallink.participation;

import com.locallink.participation.entity.Participation;
import com.locallink.participation.kafka.ParticipationKafkaProducer;
import com.locallink.participation.repository.ParticipationRepository;
import com.locallink.participation.service.ProgramServiceClient;
import com.locallink.participation.service.SettlementServiceClient;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * 같은 settlement.completed 이벤트가 2회 도착해도 참여 카운트는 1번만 증가한다.
 * 실제 Kafka 리스너(임베디드 브로커) → ParticipationService.activateParticipation 경로를 탄다.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:idempotency;MODE=MariaDB;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "eureka.client.enabled=false",
        "participation.cleanup-interval-ms=3600000"
})
@EmbeddedKafka(partitions = 3, bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class SettlementCompletedIdempotencyTest {

    private static final String TOPIC = "settlement.completed";
    private static final long USER_ID = 1L;
    private static final long PROGRAM_ID = 10L;
    private static final long MARKER_PROGRAM_ID = 11L;

    @Autowired EmbeddedKafkaBroker broker;
    @Autowired ParticipationRepository participationRepository;

    @MockitoBean ProgramServiceClient programServiceClient;
    @MockitoBean SettlementServiceClient settlementServiceClient;
    @MockitoBean ParticipationKafkaProducer kafkaProducer;

    @BeforeEach
    void setUp() {
        participationRepository.deleteAll();
        reset(programServiceClient, kafkaProducer);
    }

    @Test
    void 같은_이벤트를_2회_수신해도_카운트는_1만_증가한다() {
        participationRepository.save(Participation.builder().userId(USER_ID).programId(PROGRAM_ID).build());
        participationRepository.save(Participation.builder().userId(USER_ID).programId(MARKER_PROGRAM_ID).build());

        Map<String, Object> props = KafkaTestUtils.producerProps(broker);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        KafkaTemplate<String, Object> template =
                new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));

        Map<String, Object> event = Map.of("userId", USER_ID, "programId", PROGRAM_ID);
        // 같은 key → 같은 파티션이므로 순서가 보장된다: 중복 2회 → 마커
        template.send(TOPIC, String.valueOf(USER_ID), event);
        template.send(TOPIC, String.valueOf(USER_ID), event);
        template.send(TOPIC, String.valueOf(USER_ID), Map.of("userId", USER_ID, "programId", MARKER_PROGRAM_ID));
        template.flush();

        // 마커까지 처리됐다 = 앞선 중복 이벤트 2건도 이미 처리됨
        verify(programServiceClient, timeout(15_000)).increaseParticipationCount(MARKER_PROGRAM_ID);

        verify(programServiceClient, times(1)).increaseParticipationCount(PROGRAM_ID);
        verify(kafkaProducer, times(2)).publishParticipationCompleted(org.mockito.ArgumentMatchers.any());
        assertThat(participationRepository.findByUserIdAndProgramId(USER_ID, PROGRAM_ID).orElseThrow().getStatus())
                .isEqualTo(Participation.Status.ACTIVE);
    }
}
