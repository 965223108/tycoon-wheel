package com.tycoon.admin.bootstrap;

import com.tycoon.admin.domain.AdminUser;
import com.tycoon.admin.domain.GameSegment;
import com.tycoon.admin.mapper.AdminUserMapper;
import com.tycoon.admin.mapper.SegmentMapper;
import com.tycoon.admin.util.PasswordHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 首次启动初始化：后台管理员账号 + 转盘商品默认配置。
 */
@Component
public class AdminBootstrap {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AdminUserMapper adminUserMapper;
    private final SegmentMapper segmentMapper;

    @Value("${admin.bootstrap.username:admin}")
    private String defaultAdminUser;

    @Value("${admin.bootstrap.password:admin123}")
    private String defaultAdminPassword;

    public AdminBootstrap(AdminUserMapper adminUserMapper, SegmentMapper segmentMapper) {
        this.adminUserMapper = adminUserMapper;
        this.segmentMapper = segmentMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        seedAdmin();
        seedSegments();
    }

    private void seedAdmin() {
        if (adminUserMapper.countAll() > 0) {
            return;
        }
        String salt = PasswordHasher.newSalt();
        AdminUser u = new AdminUser();
        u.setUsername(defaultAdminUser);
        u.setSalt(salt);
        u.setPasswordHash(PasswordHasher.hash(defaultAdminPassword, salt));
        u.setRole("ADMIN");
        adminUserMapper.insert(u);
        log.info("已创建默认管理员账号: {} （请尽快修改密码）", defaultAdminUser);
    }

    private void seedSegments() {
        if (segmentMapper.countAll() > 0) {
            return;
        }
        List<GameSegment> seeds = List.of(
                seg(0, "p5", "5 积分", "💰", "points", "直接到账 5 积分", 25.0, 5),
                seg(1, "none", "谢谢参与", "🎈", "none", "这次没中，好运在下一次。", 22.5, 0),
                seg(2, "goods", "生活用品", "🧺", "prize", "生活用品礼包一份，客服会在 24 小时内联系你确认收货信息。", 8.0, 0),
                seg(3, "none2", "谢谢参与", "🎈", "none", "这次没中，好运在下一次。", 22.5, 0),
                seg(4, "coupon8", "8 折券", "🏷️", "coupon", "全站通用 8 折券，已存入账户。", 15.0, 0),
                seg(5, "sofa", "懒人沙发", "🛋️", "prize", "懒人沙发一台！客服会在 24 小时内联系你确认收货信息。", 4.0, 0),
                seg(6, "tv", "电视", "📺", "prize", "液晶电视一台！客服会在 24 小时内联系你确认收货信息。", 2.0, 0),
                seg(7, "phone", "手机", "📱", "prize", "最新款手机一部！客服会在 24 小时内联系你确认收货信息。", 1.0, 0)
        );
        seeds.forEach(segmentMapper::insert);
        log.info("已初始化 {} 个转盘商品配置", seeds.size());
    }

    private static GameSegment seg(int idx, String key, String label, String icon,
                                   String type, String desc, double percent, int points) {
        GameSegment s = new GameSegment();
        s.setIdx(idx);
        s.setSegKey(key);
        s.setLabel(label);
        s.setIcon(icon);
        s.setType(type);
        s.setDescription(desc);
        s.setPercent(percent);
        s.setPoints(points);
        s.setEnabled(true);
        return s;
    }
}
