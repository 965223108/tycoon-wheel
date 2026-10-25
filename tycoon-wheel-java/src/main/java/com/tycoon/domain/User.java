package com.tycoon.domain;

import java.time.Instant;

/**
 * 账号。纯 POJO —— 由 MyBatis 通过 Mapper XML 映射，不再依赖 JPA 注解。
 */
public class User {

    private Long id;
    private String username;
    private String salt;
    private String passwordHash;
    private int points;
    private int totalSpins;
    private String dayKey;
    private int dayCount;
    /** 乐观锁版本号，每次更新 +1，UPDATE 时带上旧值 */
    private long version;
    private Instant createdAt;

    public User() {
    }

    public User(String username, String salt, String passwordHash, int initialPoints) {
        this.username = username;
        this.salt = salt;
        this.passwordHash = passwordHash;
        this.points = initialPoints;
        this.totalSpins = 0;
        this.dayCount = 0;
        this.version = 0;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getSalt() { return salt; }
    public void setSalt(String salt) { this.salt = salt; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public int getPoints() { return points; }
    public void setPoints(int points) { this.points = points; }
    public void addPoints(int delta) { this.points += delta; }

    public int getTotalSpins() { return totalSpins; }
    public void setTotalSpins(int totalSpins) { this.totalSpins = totalSpins; }
    public void incTotalSpins() { this.totalSpins++; }

    public String getDayKey() { return dayKey; }
    public void setDayKey(String dayKey) { this.dayKey = dayKey; }

    public int getDayCount() { return dayCount; }
    public void setDayCount(int dayCount) { this.dayCount = dayCount; }
    public void incDayCount() { this.dayCount++; }

    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
