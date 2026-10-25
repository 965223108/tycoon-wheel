package com.tycoon.admin.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * JWT 签发与校验。网关用同一密钥做无状态鉴权。
 */
@Component
public class JwtService {

    private final SecretKey key;
    private final String issuer;
    private final long ttlHours;

    public JwtService(@Value("${admin.jwt.secret}") String secret,
                      @Value("${admin.jwt.issuer:tycoon-admin}") String issuer,
                      @Value("${admin.jwt.ttl-hours:12}") long ttlHours) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT secret 至少需要 32 字节");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.issuer = issuer;
        this.ttlHours = ttlHours;
    }

    /** 签发令牌 */
    public String issue(String username, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .claim("typ", "admin")
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttlHours, ChronoUnit.HOURS)))
                .signWith(key)
                .compact();
    }

    /** 解析令牌，非法或过期返回 null */
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

    public long getTtlHours() {
        return ttlHours;
    }
}
