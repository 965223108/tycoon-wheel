package com.tycoon.api.dto;

import java.io.Serializable;

/**
 * 转盘扇区 / 游戏商品配置。
 */
public class GameSegmentDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    /** 在转盘上的顺序，0 开始 */
    private Integer idx;
    /** 唯一 key，抽奖流水里记录它 */
    private String key;
    private String label;
    private String icon;
    /** points / none / prize / coupon */
    private String type;
    private String desc;
    /** 中奖概率（百分比，0-100） */
    private Double percent;
    /** 该商品对应的积分奖励（type=points 时有效） */
    private Integer points;
    private Boolean enabled;

    public GameSegmentDto() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Integer getIdx() { return idx; }
    public void setIdx(Integer idx) { this.idx = idx; }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getDesc() { return desc; }
    public void setDesc(String desc) { this.desc = desc; }

    public Double getPercent() { return percent; }
    public void setPercent(Double percent) { this.percent = percent; }

    public Integer getPoints() { return points; }
    public void setPoints(Integer points) { this.points = points; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
