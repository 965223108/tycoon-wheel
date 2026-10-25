package com.tycoon.user.domain;

import java.time.LocalDateTime;

/**
 * user_rewards 表映射。
 */
public class UserReward {

    private Long id;
    private Long userId;
    private String prizeKey;
    private String label;
    private String icon;
    private String status;
    private String remark;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getPrizeKey() { return prizeKey; }
    public void setPrizeKey(String prizeKey) { this.prizeKey = prizeKey; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
