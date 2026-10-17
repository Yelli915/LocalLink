package com.locallink.program.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    /**
     * Eureka 로드밸런싱 WebClient - http://<서비스ID>/... 로 호출한다.
     * user-service 의 /internal/** 가 공유 토큰을 요구하므로 모든 호출에 헤더를 붙인다.
     */
    @Bean
    @LoadBalanced
    public WebClient.Builder loadBalancedWebClientBuilder(@Value("${internal.api-token:}") String internalApiToken) {
        return WebClient.builder().defaultHeader(InternalApiTokenFilter.HEADER, internalApiToken);
    }
}
