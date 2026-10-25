package com.tycoon.game;

/**
 * 一个扇区。weight 是相对权重，百分比由服务端换算，前端只负责展示。
 */
public record Segment(
        int index,
        String key,
        String label,
        String icon,
        String type,     // points | prize | coupon | none
        int points,      // type=points 时奖励的积分
        String desc,
        double weight
) {
}
