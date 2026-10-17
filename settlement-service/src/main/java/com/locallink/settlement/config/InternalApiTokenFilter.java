package com.locallink.settlement.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 서비스 간 호출 전용 경로(/internal/)를 공유 토큰으로 보호한다.
 *
 * 게이트웨이는 /internal/ 경로를 라우팅하지 않지만, 컨테이너 네트워크 안(또는 루프백으로 열린 포트)에서
 * 직접 호출하면 인증 없이 통과하던 문제를 막는다. 호출하는 서비스는 X-Internal-Token 헤더에
 * INTERNAL_API_TOKEN 값을 실어 보낸다.
 *
 * 토큰이 설정돼 있지 않으면 모든 internal 요청을 거부한다 (fail closed).
 * 이 프로젝트는 학습용이라 JWT client-credentials(scope service.read) 대신 단순한 공유 시크릿을 쓴다.
 */
public class InternalApiTokenFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Token";

    private final byte[] expected;

    public InternalApiTokenFilter(String token) {
        this.expected = token == null ? new byte[0] : token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().contains("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String provided = request.getHeader(HEADER);
        boolean ok = expected.length > 0 && provided != null
                && MessageDigest.isEqual(expected, provided.getBytes(StandardCharsets.UTF_8));
        if (!ok) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"message\":\"내부 서비스 전용 API 입니다\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
