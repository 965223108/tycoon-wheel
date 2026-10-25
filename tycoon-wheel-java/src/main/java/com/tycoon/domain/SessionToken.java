package com.tycoon.domain;

import java.time.Instant;

/**
 * 服务端会话。令牌只存在 Cookie 里，账号状态全在库中。
 */
public class SessionToken {

    private String token;
    private Long userId;
    private Instant expiresAt;
    private Instant createdAt;

    public SessionToken() {
    }

    public SessionToken(String token, Long userId, Instant expiresAt) {
        this.token = token;
        this.userId = userId;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
