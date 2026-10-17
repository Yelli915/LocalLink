package com.locallink.settlement.repository;

import com.locallink.settlement.entity.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    List<Settlement> findByUserId(Long userId);

    Optional<Settlement> findByUserIdAndProgramId(Long userId, Long programId);

    Optional<Settlement> findByTransactionId(String transactionId);
}