package com.tycoon.gateway.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import reactor.core.publisher.Mono;

/**
 * 网关鉴权规则（Spring Security + JWT，无状态）。
 *
 * <pre>
 *   /api/admin/auth/login   公开（换取 token）
 *   /admin/**               公开（静态登录页；页面里的数据请求仍需 token）
 *   /api/admin/**           需要 ROLE_ADMIN
 *   其它（玩家侧）            放行，玩家侧仍由 wheel 自己的会话控制
 * </pre>
 *
 * <p>注意：路径不能写成 {@code /admin/**}{@code /*.js} —— Spring 的 PathPattern
 * 不允许 {@code **} 后面再跟内容，会直接导致容器启动失败。
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/actuator/**",
            "/api/admin/auth/login",
            "/admin",
            "/admin/**"
    };

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http,
                                                         JwtAuthWebFilter jwtFilter) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(ex -> ex
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        .pathMatchers(PUBLIC_PATHS).permitAll()
                        .pathMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyExchange().permitAll())
                .exceptionHandling(h -> h
                        .authenticationEntryPoint((exchange, ex) ->
                                writeJson(exchange, HttpStatus.UNAUTHORIZED,
                                        "{\"error\":\"UNAUTHORIZED\",\"message\":\"请先登录管理后台\"}"))
                        .accessDeniedHandler((exchange, ex) ->
                                writeJson(exchange, HttpStatus.FORBIDDEN,
                                        "{\"error\":\"FORBIDDEN\",\"message\":\"没有管理员权限\"}")))
                .addFilterAt(jwtFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

    private static Mono<Void> writeJson(org.springframework.web.server.ServerWebExchange exchange,
                                        HttpStatus status, String json) {
        var response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().set("Content-Type", "application/json;charset=UTF-8");
        var buffer = response.bufferFactory().wrap(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
