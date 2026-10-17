package com.locallink.program.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.Map;

/**
 * user-service 에서 사용자 역할을 조회해 "지자체 담당자만" 같은 서비스 레벨 권한을 검증한다.
 * 게이트웨이가 넘겨주는 것은 X-User-Id 뿐이라 역할은 여기서 직접 확인해야 한다.
 * (GET /api/users/internal/{id} - X-Internal-Token 필요, WebClientConfig 가 붙인다)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserServiceClient {

    static final String GOV_OFFICER = "GOV_OFFICER";

    private final WebClient.Builder webClientBuilder;

    @Value("${program.http-timeout-seconds:5}")
    private long httpTimeoutSeconds = 5;

    /**
     * @throws ForbiddenException                사용자가 없거나 지자체 담당자가 아닌 경우
     * @throws UserServiceUnavailableException   user-service 를 조회하지 못한 경우 (fail closed)
     */
    public void requireOfficer(Long userId) {
        String role = fetchRole(userId);
        if (!GOV_OFFICER.equals(role)) {
            throw new ForbiddenException("지자체 담당자만 프로그램을 등록할 수 있습니다");
        }
    }

    private String fetchRole(Long userId) {
        try {
            Map<String, Object> user = webClientBuilder.build()
                    .get()
                    .uri("http://user-service/api/users/internal/{id}", userId)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() { })
                    .block(Duration.ofSeconds(httpTimeoutSeconds));
            Object role = user == null ? null : user.get("role");
            return role == null ? null : role.toString();
        } catch (WebClientResponseException e) {
            // user-service 는 없는 사용자를 400(IllegalArgumentException) 또는 404 로 응답한다 → 담당자가 아님
            if (e.getStatusCode().value() == 400 || e.getStatusCode().value() == 404) {
                throw new ForbiddenException("존재하지 않는 사용자입니다");
            }
            // 그 밖의 오류(토큰 불일치로 인한 403, 5xx 등)는 "확인하지 못함" - 허용하지 않고 503
            log.error("[UserServiceClient] 역할 조회 실패 - userId: {}, status: {}", userId, e.getStatusCode());
            throw new UserServiceUnavailableException("권한을 확인하지 못했습니다. 잠시 후 다시 시도해 주세요", e);
        } catch (Exception e) {
            log.error("[UserServiceClient] 역할 조회 실패 - userId: {}, error: {}", userId, e.getMessage());
            throw new UserServiceUnavailableException("권한을 확인하지 못했습니다. 잠시 후 다시 시도해 주세요", e);
        }
    }
}
