package com.locallink.program;

import com.locallink.program.service.ForbiddenException;
import com.locallink.program.service.UserServiceClient;
import com.locallink.program.service.UserServiceUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** user-service 응답(역할)에 따라 허용/403/503 으로 매핑되는지 - 네트워크 없이 ExchangeFunction 으로 대체 */
class UserServiceClientTest {

    private UserServiceClient clientReturning(ExchangeFunction exchange, AtomicReference<URI> seen) {
        ExchangeFunction recording = request -> {
            if (seen != null) seen.set(request.url());
            return exchange.exchange(request);
        };
        return new UserServiceClient(WebClient.builder().exchangeFunction(recording));
    }

    private ClientResponse json(HttpStatus status, String body) {
        return ClientResponse.create(status)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body(body).build();
    }

    @Test
    void 지자체_담당자면_통과하고_internal_경로를_호출한다() {
        AtomicReference<URI> seen = new AtomicReference<>();
        UserServiceClient client = clientReturning(
                r -> Mono.just(json(HttpStatus.OK, "{\"id\":7,\"role\":\"GOV_OFFICER\"}")), seen);

        assertThatCode(() -> client.requireOfficer(7L)).doesNotThrowAnyException();
        assertThat(seen.get().toString()).isEqualTo("http://user-service/api/users/internal/7");
    }

    @Test
    void 소상공인이면_403_대상이다() {
        UserServiceClient client = clientReturning(
                r -> Mono.just(json(HttpStatus.OK, "{\"id\":5,\"role\":\"MERCHANT\"}")), null);

        assertThatThrownBy(() -> client.requireOfficer(5L)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void 역할이_없는_응답도_허용하지_않는다() {
        UserServiceClient client = clientReturning(
                r -> Mono.just(json(HttpStatus.OK, "{\"id\":5}")), null);

        assertThatThrownBy(() -> client.requireOfficer(5L)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void 존재하지_않는_사용자는_403_대상이다() {
        UserServiceClient client = clientReturning(
                r -> Mono.just(json(HttpStatus.NOT_FOUND, "{}")), null);

        assertThatThrownBy(() -> client.requireOfficer(99L)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void user_service가_없는_사용자를_400으로_응답해도_403_대상이다() {
        UserServiceClient client = clientReturning(
                r -> Mono.just(json(HttpStatus.BAD_REQUEST, "{\"success\":false}")), null);

        assertThatThrownBy(() -> client.requireOfficer(999L)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void 내부_토큰_불일치로_403을_받으면_확인_불가이므로_503_대상이다() {
        UserServiceClient client = clientReturning(
                r -> Mono.just(json(HttpStatus.FORBIDDEN, "{}")), null);

        assertThatThrownBy(() -> client.requireOfficer(7L)).isInstanceOf(UserServiceUnavailableException.class);
    }

    @Test
    void user_service_장애는_허용하지_않고_503_대상이다() {
        UserServiceClient serverError = clientReturning(
                r -> Mono.just(json(HttpStatus.INTERNAL_SERVER_ERROR, "{}")), null);
        UserServiceClient unreachable = clientReturning(
                r -> Mono.error(new IllegalStateException("connection refused")), null);

        assertThatThrownBy(() -> serverError.requireOfficer(7L)).isInstanceOf(UserServiceUnavailableException.class);
        assertThatThrownBy(() -> unreachable.requireOfficer(7L)).isInstanceOf(UserServiceUnavailableException.class);
    }
}
