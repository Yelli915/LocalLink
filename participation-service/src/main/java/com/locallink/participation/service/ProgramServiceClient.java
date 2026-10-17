package com.locallink.participation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProgramServiceClient {

    private final WebClient.Builder webClientBuilder;

    /** 응답이 없으면 스레드/커넥션을 무한정 붙잡지 않도록 모든 호출에 상한을 둔다 */
    @Value("${participation.http-timeout-seconds:5}")
    private long httpTimeoutSeconds = 5;

    private Duration timeout() {
        return Duration.ofSeconds(httpTimeoutSeconds);
    }

    /**
     * Program Service: 프로그램 존재 여부 확인 (동기 REST)
     */
    public boolean existsProgram(Long programId) {
        try {
            Boolean exists = webClientBuilder.build()
                    .get()
                    .uri("http://program-service/api/programs/internal/exists/{id}", programId)
                    .retrieve()
                    .bodyToMono(Boolean.class)
                    .block(timeout());

            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.error("[ProgramServiceClient] 프로그램 존재 확인 실패 - programId: {}, error: {}",
                    programId, e.getMessage());
            throw new RuntimeException("Program Service 연결 실패");
        }
    }

    /**
     * Program Service: 프로그램 상세 조회
     * - 내 참여 목록 응답에 program 정보를 붙일 때 사용
     * - program-service 쪽에 GET /api/programs/internal/{id} 엔드포인트가 있어야 함
     */
    public Map<String, Object> getProgram(Long programId) {
        try {
            Map<String, Object> responseBody = webClientBuilder.build()
                    .get()
                    .uri("http://program-service/api/programs/internal/{id}", programId)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                    })
                    .block(timeout());

            if (responseBody == null) {
                throw new RuntimeException("Program Service 응답 본문이 비어 있습니다.");
            }

            log.info("[ProgramServiceClient] 프로그램 상세 조회 성공 - programId: {}", programId);
            log.debug("[ProgramServiceClient] 프로그램 상세 응답 - programId: {}, body: {}", programId, responseBody);

            /*
             * 응답 형태가 다음 둘 중 하나일 수 있으므로 둘 다 처리
             *
             * 1) 래퍼 응답
             * {
             * "success": true,
             * "message": "성공",
             * "data": { ...program fields... }
             * }
             *
             * 2) 바로 프로그램 객체 반환
             * {
             * "id": 1,
             * "title": "...",
             * ...
             * }
             */
            Object data = responseBody.get("data");
            if (data instanceof Map<?, ?> dataMap) {
                @SuppressWarnings("unchecked")
                Map<String, Object> programMap = (Map<String, Object>) dataMap;
                return programMap;
            }

            return responseBody;
        } catch (WebClientResponseException.NotFound e) {
            throw new IllegalArgumentException("존재하지 않는 프로그램입니다: " + programId);
        } catch (Exception e) {
            log.error("[ProgramServiceClient] 프로그램 상세 조회 실패 - programId: {}, error: {}",
                    programId, e.getMessage());
            throw new RuntimeException("Program Service 프로그램 상세 조회 실패");
        }
    }

    /**
     * 공동물류 프로그램의 최종 소상공인 부담금 조회 (수정)
     */
    public BigDecimal getProgramPrice(Long programId) {
        Map<String, Object> programInfo = getProgram(programId);

        Object priceValue = programInfo.get("price");

        if (priceValue == null) {
            throw new IllegalArgumentException(
                    "프로그램 가격 정보가 없습니다: " + programId);
        }

        BigDecimal price = new BigDecimal(priceValue.toString());

        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "프로그램 가격은 0보다 커야 합니다: " + programId);
        }

        return price;
    }

    /**
     * Program Service: 참여자 수 증가 (참여 활성화 시 호출)
     */
    public void increaseParticipationCount(Long programId) {
        try {
            webClientBuilder.build()
                    .post()
                    .uri("http://program-service/api/programs/internal/{id}/participation-count", programId)
                    .retrieve()
                    .toBodilessEntity()
                    .block(timeout());

            log.info("[ProgramServiceClient] 참여자 수 증가 완료 - programId: {}", programId);
        } catch (Exception e) {
            log.error("[ProgramServiceClient] 참여자 수 증가 실패 - programId: {}, error: {}",
                    programId, e.getMessage());
        }
    }
}