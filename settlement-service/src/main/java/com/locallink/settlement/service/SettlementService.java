package com.locallink.settlement.service;

import com.locallink.settlement.dto.SettlementDto;
import com.locallink.settlement.entity.Settlement;
import com.locallink.settlement.kafka.SettlementKafkaProducer;
import com.locallink.settlement.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementService {

    private final SettlementRepository settlementRepository;
    private final SettlementKafkaProducer kafkaProducer;

    /**
     * 내부 정산 요청 (Participation Service → Settlement Service REST 호출)
     * 이 프로젝트는 PG 연동 없이 항상 성공으로 처리
     *
     * 처리 흐름:
     * 1. Settlement 생성 (PENDING)
     * 2. PG 정산 처리 (시연용: UUID 트랜잭션 ID 발급으로 대체)
     * 3. Settlement 상태 → COMPLETED
     * 4. settlement.completed 이벤트 발행 → Kafka
     */
    @Transactional
    public SettlementDto.InternalSettlementResult processInternalSettlement(
            SettlementDto.InternalSettlementRequest request) {

        log.info("[SettlementService] 정산 요청 - userId: {}, programId: {}, amount: {}",
                request.getUserId(), request.getProgramId(), request.getAmount());

        Settlement settlement = settlementRepository.save(
                Settlement.builder()
                        .userId(request.getUserId())
                        .programId(request.getProgramId())
                        .amount(request.getAmount())
                        .build()
        );

        try {
            String transactionId = UUID.randomUUID().toString();

            settlement.complete(transactionId);
            log.info("[SettlementService] 정산 완료 처리 - settlementId: {}, transactionId: {}",
                    settlement.getId(), transactionId);

            kafkaProducer.publishSettlementCompleted(
                    SettlementKafkaProducer.SettlementCompletedEvent.builder()
                            .settlementId(settlement.getId())
                            .userId(request.getUserId())
                            .programId(request.getProgramId())
                            .status("COMPLETED")
                            .build()
            );

            log.info("[SettlementService] 정산 최종 성공 - settlementId: {}", settlement.getId());

            return SettlementDto.InternalSettlementResult.builder()
                    .settlementId(settlement.getId())
                    .status("COMPLETED")
                    .build();

        } catch (Exception e) {
            settlement.fail();

            log.error("[SettlementService] 정산 실패 - settlementId: {}, userId: {}, programId: {}, error: {}",
                    settlement.getId(),
                    request.getUserId(),
                    request.getProgramId(),
                    e.getMessage(),
                    e);

            return SettlementDto.InternalSettlementResult.builder()
                    .settlementId(settlement.getId())
                    .status("FAILED")
                    .build();
        }
    }

    /**
     * 정산 단건 조회
     */
    public SettlementDto.SettlementResponse getSettlement(Long id) {
        Settlement settlement = settlementRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("정산 정보를 찾을 수 없습니다: " + id));
        return SettlementDto.SettlementResponse.from(settlement);
    }

    /**
     * 사용자 정산 내역 조회
     */
    public List<SettlementDto.SettlementResponse> getSettlementsByUser(Long userId) {
        return settlementRepository.findByUserId(userId).stream()
                .map(SettlementDto.SettlementResponse::from)
                .collect(Collectors.toList());
    }
}