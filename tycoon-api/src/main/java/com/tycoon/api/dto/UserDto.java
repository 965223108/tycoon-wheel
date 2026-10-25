package com.tycoon.api.dto;

import java.io.Serializable;

/**
 * 用户视图。跨进程传输，必须实现 Serializable（Dubbo 默认 hessian2 序列化）。
 */
public class UserDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String username;
    private String nickname;
    private Integer points;
    private Integer totalSpins;
    private String tier;
    /** ACTIVE / BLACKLISTED */
    private String status;
    private Long createdAt;
    private String blacklistReason;

    public UserDto() {
    }

    public static UserDto of(Long id, String username, Integer points, Integer totalSpins, String tier) {
        UserDto u = new UserDto();
        u.id = id;
        u.username = username;
        u.points = points;
        u.totalSpins = totalSpins;
        u.tier = tier;
        u.status = "ACTIVE";
        return u;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public Integer getPoints() { return points; }
    public void setPoints(Integer points) { this.points = points; }

    public Integer getTotalSpins() { return totalSpins; }
    public void setTotalSpins(Integer totalSpins) { this.totalSpins = totalSpins; }

    public String getTier() { return tier; }
    public void setTier(String tier) { this.tier = tier; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }

    public String getBlacklistReason() { return blacklistReason; }
    public void setBlacklistReason(String blacklistReason) { this.blacklistReason = blacklistReason; }
}
