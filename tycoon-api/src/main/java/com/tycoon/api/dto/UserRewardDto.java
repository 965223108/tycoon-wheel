package com.tycoon.api.dto;

import java.io.Serializable;

/**
 * 用户获得的奖励 / 奖品记录。
 */
public class UserRewardDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private String username;
    /** 奖品 key，例如 goods / coupon8 / tv / phone */
    private String prizeKey;
    private String label;
    private String icon;
    /** ISSUED 已发放 / PENDING 待发放 / CANCELLED 已取消 */
    private String status;
    private String remark;
    private Long createdAt;

    public UserRewardDto() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

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

    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }
}
