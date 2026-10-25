package com.tycoon.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * 网关侧 JWT 校验。密钥必须与 admin-service 一致（环境变量 JWT_SECRET）。
 */
@Component
public class JwtService {

    private final SecretKey key;
    private final String issuer;

    public JwtService(@Value("${gateway.jwt.secret}") String secret,
                      @Value("${gateway.jwt.issuer:tycoon-admin}") String issuer) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT secret 至少需要 32 字节");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.issuer = issuer;
    }

    /** 解析令牌；无效或过期返回 null */
    public Claims parse(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String t = token.startsWith("Bearer ") ? token.substring(7) : token;
        try {
            return Jwts.parser().verifyWith(key).requireIssuer(issuer).build()
                    .parseSignedClaims(t).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}
