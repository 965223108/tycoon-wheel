package com.tycoon.game;

import com.tycoon.api.dto.GameSegmentDto;
import com.tycoon.api.game.GameConfigDubboService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.List;

/**
 * 转盘盘面与抽奖算法。
 *
 * <p>盘面不再硬编码：通过 <b>Dubbo</b> 从 admin-service 拉取后台配置的商品与概率，
 * 每 10 秒刷新一次；拉不到时回退到内置盘面，保证游戏不会因为后台服务抖动而不可用。
 *
 * <p>中奖结果只在服务端产生，前端拿到 segmentIndex 后只负责把轮盘转到位。
 */
@Component
public class WheelCatalog {

    private static final Logger log = LoggerFactory.getLogger(WheelCatalog.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long REFRESH_MS = 10_000L;

    /** 兜底盘面：admin-service 不可用时使用 */
    private static final List<Segment> FALLBACK = List.of(
            new Segment(0, "p5",     "5 积分",   "💰", "points", 5,  "积分已入账，可以继续抽。", 25),
            new Segment(1, "none",   "谢谢参与", "🎈", "none",   0,  "这次没中，好运在下一次。", 22.5),
            new Segment(2, "goods",  "生活用品", "🧺", "prize",  0,  "生活用品礼包一份，客服会在 24 小时内联系你确认收货信息。", 8),
            new Segment(3, "none2",  "谢谢参与", "🎈", "none",   0,  "差一点点，再试一次？", 22.5),
            new Segment(4, "coupon", "8 折券",   "🏷️", "coupon", 0,  "积分商城 8 折优惠券，已存入账户。", 15),
            new Segment(5, "sofa",   "懒人沙发", "🛋️", "prize",  0,  "懒人沙发一台！客服会在 24 小时内联系你确认收货信息。", 4),
            new Segment(6, "tv",     "电视",     "📺", "prize",  0,  "电视机一台！请保持手机畅通，客服将联系你核对地址。", 2),
            new Segment(7, "phone",  "手机",     "📱", "prize",  0,  "手机一部！这是最稀有的奖品，客服会尽快联系你。", 1)
    );

    @DubboReference(interfaceClass = GameConfigDubboService.class,
            check = false, timeout = 3000, retries = 0)
    private GameConfigDubboService gameConfig;

    private volatile List<Segment> current = FALLBACK;
    private volatile double currentWeight = sumWeight(FALLBACK);
    private volatile long lastLoad = 0L;

    /** 当前生效的盘面（按需刷新） */
    public List<Segment> segments() {
        refreshIfNeeded();
        return current;
    }

    public double totalWeight() {
        refreshIfNeeded();
        return currentWeight;
    }

    public double percentOf(Segment s) {
        double total = totalWeight();
        return total <= 0 ? 0 : s.weight() / total * 100.0;
    }

    /** 按权重抽取。用 SecureRandom 而不是 Random，避免种子可预测。 */
    public Segment pick() {
        refreshIfNeeded();
        List<Segment> list = current;
        double total = currentWeight;
        double r = RANDOM.nextDouble() * total;
        double acc = 0;
        for (Segment s : list) {
            acc += s.weight();
            if (r < acc) {
                return s;
            }
        }
        return list.get(list.size() - 1);
    }

    /** 强制刷新（后台改完配置想立刻生效时可调用） */
    public void invalidate() {
        lastLoad = 0L;
    }

    private void refreshIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastLoad < REFRESH_MS) {
            return;
        }
        synchronized (this) {
            if (now - lastLoad < REFRESH_MS) {
                return;
            }
            lastLoad = now;
            try {
                List<GameSegmentDto> dtos = gameConfig.listEnabledSegments();
                if (dtos == null || dtos.isEmpty()) {
                    return;
                }
                List<Segment> loaded = dtos.stream().map(WheelCatalog::toSegment).toList();
                double sum = sumWeight(loaded);
                if (sum > 0) {
                    current = loaded;
                    currentWeight = sum;
                }
            } catch (Exception e) {
                log.warn("从 admin-service 拉取游戏配置失败，继续使用当前盘面: {}", e.getMessage());
            }
        }
    }

    private static Segment toSegment(GameSegmentDto d) {
        int pts = d.getPoints() == null ? 0 : d.getPoints();
        double weight = d.getPercent() == null ? 0 : d.getPercent();
        String desc = d.getDesc() == null ? "" : d.getDesc();
        return new Segment(
                d.getIdx() == null ? 0 : d.getIdx(),
                d.getKey(), d.getLabel(), d.getIcon(), d.getType(),
                pts, desc, weight);
    }

    private static double sumWeight(List<Segment> list) {
        return list.stream().mapToDouble(Segment::weight).sum();
    }
}
