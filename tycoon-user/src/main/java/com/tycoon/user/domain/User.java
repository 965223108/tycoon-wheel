package com.tycoon.user.domain;

import java.time.LocalDateTime;

/**
 * users 表映射（历史表，由最早的 wheel 服务创建）。
 */
public class User {

    private Long id;
    private String username;
    private String salt;
    private String passwordHash;
    private Integer points;
    private Integer totalSpins;
    private String dayKey;
    private Integer dayCount;
    private Long version;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getSalt() { return salt; }
    public void setSalt(String salt) { this.salt = salt; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public Integer getPoints() { return points; }
    public void setPoints(Integer points) { this.points = points; }

    public Integer getTotalSpins() { return totalSpins; }
    public void setTotalSpins(Integer totalSpins) { this.totalSpins = totalSpins; }

    public String getDayKey() { return dayKey; }
    public void setDayKey(String dayKey) { this.dayKey = dayKey; }

    public Integer getDayCount() { return dayCount; }
    public void setDayCount(Integer dayCount) { this.dayCount = dayCount; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
