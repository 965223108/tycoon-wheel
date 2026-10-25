package com.tycoon.admin.web;

import com.tycoon.admin.domain.GameSegment;
import com.tycoon.admin.mapper.SegmentMapper;
import com.tycoon.admin.service.GameConfigServiceImpl;
import com.tycoon.api.dto.PageResult;
import com.tycoon.api.dto.UserDto;
import com.tycoon.api.dto.UserRewardDto;
import com.tycoon.api.user.UserDubboService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台管理接口。
 *
 * <p>用户 / 奖励 / 拉黑全部通过 <b>Dubbo</b> 调用 user-service；
 * 游戏商品配置写自己的 game_segments 表，并通过 Dubbo 提供给 wheel-service。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @DubboReference(interfaceClass = UserDubboService.class, check = false, timeout = 5000, retries = 0)
    private UserDubboService userService;

    private final SegmentMapper segmentMapper;

    public AdminController(SegmentMapper segmentMapper) {
        this.segmentMapper = segmentMapper;
    }

    /* ==================== 概览 ==================== */

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "service", "tycoon-admin");
    }

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userTotal", userService.listUsers(null, 1, 1).getTotal());
        m.put("segmentTotal", segmentMapper.countAll());
        m.put("segmentEnabled", segmentMapper.listEnabled().size());
        return m;
    }

    /* ==================== 用户管理 ==================== */

    @GetMapping("/users")
    public PageResult<UserDto> listUsers(@RequestParam(required = false) String keyword,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "10") int size) {
        return userService.listUsers(keyword, page, size);
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<?> getUser(@PathVariable Long id) {
        UserDto u = userService.findById(id);
        if (u == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(u);
    }

    public record PointsRequest(int delta) {
    }

    /** 运营调整积分：正数发放，负数扣回 */
    @PostMapping("/users/{id}/points")
    public Map<String, Object> adjustPoints(@PathVariable Long id, @RequestBody PointsRequest req) {
        int latest = userService.addPoints(id, req.delta());
        return Map.of("userId", id, "points", latest);
    }

    public record BlacklistRequest(String reason) {
    }

    @PostMapping("/users/{id}/blacklist")
    public Map<String, Object> blacklist(@PathVariable Long id, @RequestBody(required = false) BlacklistRequest req) {
        String reason = req == null || req.reason() == null || req.reason().isBlank()
                ? "管理员手动拉黑" : req.reason();
        userService.blacklist(id, reason);
        return Map.of("userId", id, "status", "BLACKLISTED", "reason", reason);
    }

    @PostMapping("/users/{id}/unblacklist")
    public Map<String, Object> unblacklist(@PathVariable Long id) {
        userService.unblacklist(id);
        return Map.of("userId", id, "status", "ACTIVE");
    }

    /* ==================== 奖励管理 ==================== */

    @GetMapping("/users/{id}/rewards")
    public PageResult<UserRewardDto> listRewards(@PathVariable Long id,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "10") int size) {
        return userService.listRewards(id, page, size);
    }

    public record GrantRewardRequest(Long userId, String prizeKey, String label, String icon) {
    }

    @PostMapping("/rewards")
    public Map<String, Object> grantReward(@RequestBody GrantRewardRequest req) {
        Long rewardId = userService.grantReward(req.userId(), req.prizeKey(), req.label(), req.icon());
        return Map.of("rewardId", rewardId, "userId", req.userId());
    }

    public record RewardStatusRequest(String status, String remark) {
    }

    @PostMapping("/rewards/{id}/status")
    public Map<String, Object> updateRewardStatus(@PathVariable Long id, @RequestBody RewardStatusRequest req) {
        userService.updateRewardStatus(id, req.status(), req.remark());
        return Map.of("rewardId", id, "status", req.status());
    }

    /* ==================== 游戏商品配置 ==================== */

    @GetMapping("/segments")
    public List<Map<String, Object>> listSegments() {
        return segmentMapper.listAll().stream().map(AdminController::segmentView).toList();
    }

    @PostMapping("/segments")
    public synchronized Map<String, Object> createSegment(@RequestBody GameSegment body) {
        if (segmentMapper.findByKey(body.getSegKey()) != null) {
            throw new IllegalArgumentException("key 已存在: " + body.getSegKey());
        }
        if (body.getIdx() == null) {
            body.setIdx((int) segmentMapper.countAll());
        }
        if (body.getEnabled() == null) {
            body.setEnabled(true);
        }
        if (body.getPoints() == null) {
            body.setPoints(0);
        }
        segmentMapper.insert(body);
        return Map.of("id", body.getId(), "key", body.getSegKey());
    }

    @PutMapping("/segments/{id}")
    public Map<String, Object> updateSegment(@PathVariable Long id, @RequestBody GameSegment body) {
        GameSegment exist = segmentMapper.findById(id);
        if (exist == null) {
            throw new IllegalArgumentException("商品不存在: " + id);
        }
        body.setId(id);
        body.setSegKey(exist.getSegKey());
        if (body.getIdx() == null) body.setIdx(exist.getIdx());
        if (body.getEnabled() == null) body.setEnabled(exist.getEnabled());
        if (body.getPoints() == null) body.setPoints(exist.getPoints());
        segmentMapper.update(body);
        return Map.of("id", id, "updated", true);
    }

    @DeleteMapping("/segments/{id}")
    public Map<String, Object> deleteSegment(@PathVariable Long id) {
        segmentMapper.delete(id);
        return Map.of("id", id, "deleted", true);
    }

    @PostMapping("/segments/{id}/toggle")
    public Map<String, Object> toggleSegment(@PathVariable Long id) {
        GameSegment s = segmentMapper.findById(id);
        if (s == null) {
            throw new IllegalArgumentException("商品不存在: " + id);
        }
        boolean next = !Boolean.TRUE.equals(s.getEnabled());
        segmentMapper.setEnabled(id, next);
        return Map.of("id", id, "enabled", next);
    }

    private static Map<String, Object> segmentView(GameSegment s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("idx", s.getIdx());
        m.put("key", s.getSegKey());
        m.put("label", s.getLabel());
        m.put("icon", s.getIcon());
        m.put("type", s.getType());
        m.put("desc", s.getDescription());
        m.put("percent", s.getPercent());
        m.put("points", s.getPoints());
        m.put("enabled", s.getEnabled());
        return m;
    }

    /** 供轮询校验：当前启用扇区概率合计 */
    @GetMapping("/segments/percent-sum")
    public Map<String, Object> percentSum() {
        double sum = segmentMapper.listEnabled().stream()
                .mapToDouble(s -> s.getPercent() == null ? 0 : s.getPercent()).sum();
        return Map.of("sum", Math.round(sum * 100.0) / 100.0);
    }
}
