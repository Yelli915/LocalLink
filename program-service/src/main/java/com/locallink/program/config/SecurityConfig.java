package com.locallink.program.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity

public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            @org.springframework.beans.factory.annotation.Value("${internal.api-token:}") String internalApiToken)
            throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(request -> {
                var config = new org.springframework.web.cors.CorsConfiguration();
                config.addAllowedOriginPattern("*");
                config.addAllowedMethod("*");
                config.addAllowedHeader("*");
                return config;
            }))
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()
            )
            // /internal/** 는 서비스 간 공유 토큰이 있어야만 통과 (그 외 경로는 기존과 동일)
            .addFilterBefore(new InternalApiTokenFilter(internalApiToken),
                org.springframework.security.web.access.intercept.AuthorizationFilter.class);
        return http.build();
    }
}

// public class SecurityConfig {
//     @Bean
//     public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
//         http
//             .csrf(csrf -> csrf.disable())
            // .cors(cors -> cors.configurationSource(request -> {
            //     var config = new org.springframework.web.cors.CorsConfiguration();
            //     config.addAllowedOriginPattern("*");
            //     config.addAllowedMethod("*");
            //     config.addAllowedHeader("*");
            //     return config;
            // }))
//             .sessionManagement(session ->
//                 session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
//             .authorizeHttpRequests(auth -> auth
//                 .requestMatchers(
//                     "/api-docs/**",
//                     "/swagger-ui/**",
//                     "/swagger-ui.html"
//                 ).permitAll()
//                 // 프로그램 목록/상세 조회는 인증 불필요
//                 .requestMatchers(HttpMethod.GET, "/api/programs", "/api/programs/**").permitAll()
//                 // 내부 서비스 호출 (Client Credentials)
//                 .requestMatchers("/api/programs/internal/**").hasAuthority("SCOPE_service.read")
//                 // 프로그램 등록은 GOV_OFFICER만
//                 .requestMatchers(HttpMethod.POST, "/api/programs").hasAuthority("ROLE_GOV_OFFICER")
//                 .anyRequest().authenticated()
//             )
//             .oauth2ResourceServer(oauth2 -> oauth2
//                 .jwt(jwt -> {})
//             );

//         return http.build();
//     }
// }