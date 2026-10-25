package com.tycoon.domain;

import java.time.Instant;

/**
 * 一次抽奖的流水。奖品发放以这张表为准。
 */
public class SpinRecord {

    private Long id;
    private Long userId;
    private String segmentKey;
    private String label;
    private String icon;
    /** points | prize | coupon | none */
    private String type;
    /** 本次对积分的净影响（已含抽奖成本） */
    private int pointsDelta;
    /** 抽奖后余额，便于运营对账 */
    private int pointsAfter;
    private Instant createdAt;

    public SpinRecord() {
    }

    public SpinRecord(Long userId, String segmentKey, String label, String icon,
                      String type, int pointsDelta, int pointsAfter) {
        this.userId = userId;
        this.segmentKey = segmentKey;
        this.label = label;
        this.icon = icon;
        this.type = type;
        this.pointsDelta = pointsDelta;
        this.pointsAfter = pointsAfter;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getSegmentKey() { return segmentKey; }
    public void setSegmentKey(String segmentKey) { this.segmentKey = segmentKey; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public int getPointsDelta() { return pointsDelta; }
    public void setPointsDelta(int pointsDelta) { this.pointsDelta = pointsDelta; }

    public int getPointsAfter() { return pointsAfter; }
    public void setPointsAfter(int pointsAfter) { this.pointsAfter = pointsAfter; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
