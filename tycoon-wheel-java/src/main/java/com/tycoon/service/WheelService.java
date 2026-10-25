package com.tycoon.service;

import com.tycoon.api.dto.UserDto;
import com.tycoon.api.user.UserDubboService;
import com.tycoon.config.GameProperties;
import com.tycoon.domain.SpinRecord;
import com.tycoon.game.Segment;
import com.tycoon.game.WheelCatalog;
import com.tycoon.mapper.SpinRecordMapper;
import com.tycoon.web.ApiException;
import org.apache.dubbo.config.annotation.DubboReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 抽奖核心。
 *
 * <p>积分扣减、奖励发放、黑名单校验全部通过 <b>Dubbo</b> 调用 user-service；
 * 本服务只负责抽奖算法与自己的 spin_records 流水。
 */
@Service
public class WheelService {

    private static final Logger log = LoggerFactory.getLogger(WheelService.class);

    @DubboReference(interfaceClass = UserDubboService.class,
            check = false, timeout = 5000, retries = 0)
    private UserDubboService userService;

    private final SpinRecordMapper records;
    private final WheelCatalog catalog;
    private final GameProperties props;

    /** 每账号最近一分钟的抽奖时间戳，用于限速 */
    private final Map<Long, Deque<Long>> recent = new ConcurrentHashMap<>();

    public WheelService(SpinRecordMapper records, WheelCatalog catalog, GameProperties props) {
        this.records = records;
        this.catalog = catalog;
        this.props = props;
    }

    public record Outcome(Segment segment, int pointsDelta, int points, int totalSpins) {
    }

    public Outcome spin(UserDto user) {
        rateLimit(user.getId());

        if (userService.isBlacklisted(user.getId())) {
            throw ApiException.forbidden("ACCOUNT_BLOCKED", "账号已被限制，请联系客服");
        }

        int cost = props.getCostPerSpin();
        if (user.getPoints() == null || user.getPoints() < cost) {
            throw ApiException.badRequest("INSUFFICIENT_POINTS", "积分不足，抽奖需要 " + cost + " 积分");
        }

        Segment segment = catalog.pick();

        // 原子扣分：积分不足时返回 false，避免并发下扣成负数
        boolean deducted;
        try {
            deducted = userService.deductPoints(user.getId(), cost, 1);
        } catch (Exception e) {
            log.error("调用 user-service 扣分失败 userId={}", user.getId(), e);
            throw ApiException.badRequest("USER_SERVICE_UNAVAILABLE", "用户服务暂时不可用，请稍后再试");
        }
        if (!deducted) {
            throw ApiException.badRequest("INSUFFICIENT_POINTS", "积分不足，抽奖需要 " + cost + " 积分");
        }

        int delta = -cost;
        if ("points".equals(segment.type()) && segment.points() > 0) {
            delta += segment.points();
            try {
                userService.addPoints(user.getId(), segment.points());
            } catch (Exception e) {
                log.error("积分奖励发放失败 userId={} points={}", user.getId(), segment.points(), e);
            }
        }

        // 实物 / 优惠券类奖励进入用户奖励池，由后台发放
        if ("prize".equals(segment.type()) || "coupon".equals(segment.type())) {
            try {
                userService.grantReward(user.getId(), segment.key(), segment.label(), segment.icon());
            } catch (Exception e) {
                log.error("奖励记录写入失败 userId={} prize={}", user.getId(), segment.key(), e);
            }
        }

        UserDto latest = userService.findById(user.getId());
        int pointsAfter = latest == null || latest.getPoints() == null ? 0 : latest.getPoints();
        int totalSpins = latest == null || latest.getTotalSpins() == null ? 0 : latest.getTotalSpins();

        records.insert(new SpinRecord(user.getId(), segment.key(), segment.label(),
                segment.icon(), segment.type(), delta, pointsAfter));

        return new Outcome(segment, delta, pointsAfter, totalSpins);
    }

    public List<SpinRecord> history(UserDto user) {
        int keep = Math.max(1, props.getHistoryKeep());
        return records.findRecent(user.getId(), keep);
    }

    /** 每分钟最多抽 N 次，防止脚本刷 */
    private void rateLimit(Long userId) {
        long now = System.currentTimeMillis();
        Deque<Long> q = recent.computeIfAbsent(userId, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() > 60_000L) {
                q.pollFirst();
            }
            if (q.size() >= props.getMaxSpinsPerMinute()) {
                throw ApiException.tooMany("操作太频繁，请稍后再试");
            }
            q.addLast(now);
        }
    }
}
