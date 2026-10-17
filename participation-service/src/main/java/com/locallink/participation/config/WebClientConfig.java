package com.locallink.participation.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    /**
     * Eureka 로드밸런싱 WebClient
     * lb://service-name 형식으로 서비스 탐색
     */
    @Bean
    @LoadBalanced
    public WebClient.Builder loadBalancedWebClientBuilder(@Value("${internal.api-token:}") String internalApiToken) {
        // 이 빌더는 program/settlement-service 호출에만 쓰인다. 두 서비스의 /internal/** 가 이 토큰을 요구한다.
        return WebClient.builder().defaultHeader(InternalApiTokenFilter.HEADER, internalApiToken);
    }
}
