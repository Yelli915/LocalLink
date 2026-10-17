package com.locallink.settlement.dto;

import com.locallink.settlement.entity.Settlement;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class SettlementDto {

    // 정산 요청 (외부 클라이언트용)
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SettlementRequest {
        @NotNull(message = "프로그램 ID는 필수입니다")
        private Long programId;

        @NotNull(message = "금액은 필수입니다")
        @Positive(message = "금액은 양수여야 합니다")
        private BigDecimal amount;
    }

    // 내부 서비스 정산 요청 (Participation Service → Settlement Service)
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InternalSettlementRequest {
        private Long userId;
        private Long programId;
        private BigDecimal amount;
    }

    // 정산 응답
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SettlementResponse {
        private Long settlementId;
        private Long userId;
        private Long programId;
        private BigDecimal amount;
        private Settlement.Status status;
        private String transactionId;
        private LocalDateTime createdAt;

        public static SettlementResponse from(Settlement settlement) {
            return SettlementResponse.builder()
                    .settlementId(settlement.getId())
                    .userId(settlement.getUserId())
                    .programId(settlement.getProgramId())
                    .amount(settlement.getAmount())
                    .status(settlement.getStatus())
                    .transactionId(settlement.getTransactionId())
                    .createdAt(settlement.getCreatedAt())
                    .build();
        }
    }

    // 내부 서비스 정산 결과 응답
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InternalSettlementResult {
        private Long settlementId;
        private String status;
    }

    // 공통 API 응답 래퍼
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ApiResponse<T> {
        private boolean success;
        private String message;
        private T data;

        public static <T> ApiResponse<T> success(T data) {
            return ApiResponse.<T>builder()
                    .success(true)
                    .message("성공")
                    .data(data)
                    .build();
        }

        public static <T> ApiResponse<T> error(String message) {
            return ApiResponse.<T>builder()
                    .success(false)
                    .message(message)
                    .build();
        }
    }
}
