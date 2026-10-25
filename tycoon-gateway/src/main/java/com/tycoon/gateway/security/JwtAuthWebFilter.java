package com.tycoon.gateway.security;

import io.jsonwebtoken.Claims;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 从 Authorization 头解析 JWT 并注入 SecurityContext。
 *
 * <p>解析失败不直接拒绝，交给 SecurityConfig 的授权规则决定（公开路径可继续）。
 */
@Component
public class JwtAuthWebFilter implements WebFilter {

    private final JwtService jwtService;

    public JwtAuthWebFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        Claims claims = jwtService.parse(header);

        if (claims == null) {
            return chain.filter(exchange);
        }

        Object role = claims.get("role");
        String authority = "ROLE_" + (role == null ? "USER" : String.valueOf(role));
        var authentication = new UsernamePasswordAuthenticationToken(
                claims.getSubject(), null, List.of(new SimpleGrantedAuthority(authority)));

        return chain.filter(exchange)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
    }
}
