package com.tycoon.web;

import java.util.List;

/**
 * 所有 HTTP 出入参。集中放一处，方便对照接口文档。
 */
public final class Dto {

    private Dto() {
    }

    // ---------- 请求 ----------
    public record CredentialsReq(String username, String password) {
    }

    // ---------- 响应 ----------
    public record SegmentView(
            int index, String key, String label, String icon,
            String type, String desc, double percent) {
    }

    public record ConfigView(
            int costPerSpin, int initialPoints, int dailyLimit, List<SegmentView> segments) {
    }

    public record HistoryItem(
            String label, String icon, String type, int pointsDelta, long at) {
    }

    public record MeView(
            String username, int points, int totalSpins, int spinsLeft,
            String tier, long createdAt, List<HistoryItem> history) {
    }

    public record SpinResult(
            int segmentIndex, SegmentView segment, String message,
            int pointsDelta, int points, int spinsLeft, int totalSpins,
            List<HistoryItem> history) {
    }

    public record StatsView(
            long users, long spins, long prizesIssued, long couponsIssued,
            long pointsAwardedByWheel) {
    }

    /** 用于验证负载均衡：访问 /api/instance 可看到请求落在哪个容器上 */
    public record InstanceView(String instance, String port) {
    }

    public record ApiError(String error, String message) {
    }
}
