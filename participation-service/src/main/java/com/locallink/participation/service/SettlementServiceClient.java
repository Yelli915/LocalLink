package com.locallink.participation.service;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementServiceClient {

    private final WebClient.Builder webClientBuilder;

    @Value("${participation.http-timeout-seconds:5}")
    private long httpTimeoutSeconds = 5;

    /**
     * Settlement Service: 정산 요청 (동기 REST)
     *
     * @throws SettlementOutcomeUnknownException 요청은 갔지만 결과를 모르는 경우 (타임아웃, 5xx)
     * @throws RuntimeException               정산이 처리되지 않았음이 확실한 경우 (연결 불가, 4xx)
     */
    public SettlementResult requestSettlement(Long userId, Long programId, BigDecimal amount) {
        try {
            SettlementRequest request = new SettlementRequest(userId, programId, amount);

            SettlementResult result = webClientBuilder.build()
                    .post()
                    .uri("http://settlement-service/api/settlements/internal/request")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(SettlementResult.class)
                    .block(Duration.ofSeconds(httpTimeoutSeconds));

            log.info("[SettlementServiceClient] 정산 요청 완료 - userId: {}, programId: {}, result: {}",
                    userId, programId, result != null ? result.getStatus() : "null");

            return result;
        } catch (WebClientResponseException e) {
            log.error("[SettlementServiceClient] 정산 요청 실패 - userId: {}, programId: {}, status: {}",
                    userId, programId, e.getStatusCode(), e);
            if (e.getStatusCode().is5xxServerError()) {
                // 서버가 처리 도중 실패 - 이벤트는 이미 발행됐을 수도 있다
                throw new SettlementOutcomeUnknownException("Settlement Service 오류 응답: " + e.getStatusCode(), e);
            }
            throw new RuntimeException("Settlement Service 가 정산 요청을 거절했습니다: " + e.getStatusCode());
        } catch (Exception e) {
            log.error("[SettlementServiceClient] 정산 요청 실패 - userId: {}, programId: {}, error: {}",
                    userId, programId, e.getMessage(), e);
            if (isResponseTimeout(e)) {
                throw new SettlementOutcomeUnknownException("Settlement Service 응답 대기 시간 초과", e);
            }
            throw new RuntimeException("Settlement Service 연결 실패");
        }
    }

    /** 연결 자체가 안 된 경우(ConnectTimeout)는 요청이 가지 않았으므로 "응답 타임아웃" 이 아니다 */
    private static boolean isResponseTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String name = t.getClass().getSimpleName();
            if (name.contains("ConnectTimeout")) {
                return false;
            }
            if (name.contains("Timeout") || t instanceof java.util.concurrent.TimeoutException
                    || (t.getMessage() != null && t.getMessage().startsWith("Timeout on blocking read"))) {
                return true;
            }
        }
        return false;
    }

    @Getter
    @NoArgsConstructor
    static class SettlementRequest {
        private Long userId;
        private Long programId;
        private BigDecimal amount;

        SettlementRequest(Long userId, Long programId, BigDecimal amount) {
            this.userId = userId;
            this.programId = programId;
            this.amount = amount;
        }
    }

    @Getter
    @NoArgsConstructor
    public static class SettlementResult {
        private Long settlementId;
        private String status; // COMPLETED / FAILED
    }
}