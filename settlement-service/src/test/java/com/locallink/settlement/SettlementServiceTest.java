package com.locallink.settlement;

import com.locallink.settlement.dto.SettlementDto;
import com.locallink.settlement.entity.Settlement;
import com.locallink.settlement.kafka.SettlementKafkaProducer;
import com.locallink.settlement.repository.SettlementRepository;
import com.locallink.settlement.service.SettlementService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 정산 처리 규칙: 성공하면 settlement.completed 를 발행하고, 처리 중 예외가 나면 FAILED 로 응답한다.
 * (DB/Kafka 없이 협력 객체를 목으로 대체)
 */
class SettlementServiceTest {

    private final SettlementRepository settlementRepository = mock(SettlementRepository.class);
    private final SettlementKafkaProducer kafkaProducer = mock(SettlementKafkaProducer.class);
    private final SettlementService settlementService = new SettlementService(settlementRepository, kafkaProducer);

    private SettlementDto.InternalSettlementRequest request() {
        return SettlementDto.InternalSettlementRequest.builder()
                .userId(1L).programId(10L).amount(BigDecimal.valueOf(99000)).build();
    }

    @Test
    void 성공하면_COMPLETED를_반환하고_settlement_completed_이벤트를_발행한다() {
        when(settlementRepository.save(any(Settlement.class))).thenAnswer(inv -> inv.getArgument(0));

        SettlementDto.InternalSettlementResult result = settlementService.processInternalSettlement(request());

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        ArgumentCaptor<SettlementKafkaProducer.SettlementCompletedEvent> captor =
                ArgumentCaptor.forClass(SettlementKafkaProducer.SettlementCompletedEvent.class);
        verify(kafkaProducer).publishSettlementCompleted(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(1L);
        assertThat(captor.getValue().getProgramId()).isEqualTo(10L);
        assertThat(captor.getValue().getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void 이벤트_발행에서_예외가_나면_FAILED로_응답한다() {
        when(settlementRepository.save(any(Settlement.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("broker down")).when(kafkaProducer).publishSettlementCompleted(any());

        SettlementDto.InternalSettlementResult result = settlementService.processInternalSettlement(request());

        assertThat(result.getStatus()).isEqualTo("FAILED");
    }
}
