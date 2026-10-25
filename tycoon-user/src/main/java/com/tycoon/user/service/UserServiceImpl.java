package com.tycoon.user.service;

import com.tycoon.api.dto.PageResult;
import com.tycoon.api.dto.UserDto;
import com.tycoon.api.dto.UserRewardDto;
import com.tycoon.api.exception.RpcBusinessException;
import com.tycoon.api.user.UserDubboService;
import com.tycoon.user.domain.User;
import com.tycoon.user.domain.UserProfile;
import com.tycoon.user.domain.UserReward;
import com.tycoon.user.mapper.ProfileMapper;
import com.tycoon.user.mapper.RewardMapper;
import com.tycoon.user.mapper.UserMapper;
import com.tycoon.user.util.PasswordHasher;
import org.apache.dubbo.config.annotation.DubboService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 用户领域实现。所有对 users 表的写操作都收敛在这里，
 * 避免 wheel / admin 各自直接改库导致状态不一致。
 */
@DubboService(interfaceClass = UserDubboService.class)
public class UserServiceImpl implements UserDubboService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private static final String ACTIVE = "ACTIVE";
    private static final String BLACKLISTED = "BLACKLISTED";

    private final UserMapper userMapper;
    private final ProfileMapper profileMapper;
    private final RewardMapper rewardMapper;

    public UserServiceImpl(UserMapper userMapper, ProfileMapper profileMapper, RewardMapper rewardMapper) {
        this.userMapper = userMapper;
        this.profileMapper = profileMapper;
        this.rewardMapper = rewardMapper;
    }

    /* ==================== 查询 ==================== */

    @Override
    public UserDto findById(Long userId) {
        if (userId == null) {
            return null;
        }
        return toDto(userMapper.findById(userId));
    }

    @Override
    public UserDto findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        return toDto(userMapper.findByUsername(normalize(username)));
    }

    @Override
    public PageResult<UserDto> listUsers(String keyword, int page, int size) {
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 100);
        int offset = (p - 1) * s;
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        List<UserDto> items = userMapper.list(kw, offset, s).stream().map(this::toDto).toList();
        return PageResult.of(items, userMapper.count(kw), p, s);
    }

    @Override
    public boolean isBlacklisted(Long userId) {
        if (userId == null) {
            return false;
        }
        UserProfile p = profileMapper.findById(userId);
        return p != null && BLACKLISTED.equals(p.getStatus());
    }

    /* ==================== 账号 ==================== */

    @Override
    @Transactional
    public UserDto register(String username, String rawPassword, int initialPoints) {
        String name = normalize(username);
        if (userMapper.countByUsername(name) > 0) {
            throw RpcBusinessException.of("USERNAME_TAKEN", "该账号已被注册");
        }
        String salt = PasswordHasher.newSalt();

        User u = new User();
        u.setUsername(name);
        u.setSalt(salt);
        u.setPasswordHash(PasswordHasher.hash(rawPassword, salt));
        u.setPoints(initialPoints);
        u.setTotalSpins(0);
        u.setDayKey(todayKey());
        u.setDayCount(0);
        u.setVersion(0L);
        u.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        userMapper.insert(u);

        UserProfile profile = new UserProfile();
        profile.setUserId(u.getId());
        profile.setNickname(name);
        profile.setStatus(ACTIVE);
        profile.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        profileMapper.upsert(profile);

        log.info("用户注册成功 id={} username={} points={}", u.getId(), name, initialPoints);
        return toDto(u);
    }

    @Override
    public UserDto authenticate(String username, String rawPassword) {
        User u = userMapper.findByUsername(normalize(username));
        if (u == null) {
            return null;
        }
        if (!PasswordHasher.verify(rawPassword, u.getSalt(), u.getPasswordHash())) {
            return null;
        }
        return toDto(u);
    }

    /* ==================== 积分 ==================== */

    @Override
    public boolean deductPoints(Long userId, int cost, int spinsDelta) {
        if (userId == null || cost < 0) {
            return false;
        }
        int rows = userMapper.deductPoints(userId, cost, todayKey());
        if (rows > 0) {
            log.debug("扣分成功 userId={} cost={}", userId, cost);
            return true;
        }
        log.debug("扣分失败（积分不足或用户不存在） userId={} cost={}", userId, cost);
        return false;
    }

    @Override
    public int addPoints(Long userId, int delta) {
        userMapper.addPoints(userId, delta);
        User u = userMapper.findById(userId);
        return u == null ? 0 : u.getPoints();
    }

    /* ==================== 黑名单 ==================== */

    @Override
    public void blacklist(Long userId, String reason) {
        ensureProfile(userId);
        profileMapper.updateStatus(userId, BLACKLISTED, reason);
        log.info("拉黑用户 userId={} reason={}", userId, reason);
    }

    @Override
    public void unblacklist(Long userId) {
        ensureProfile(userId);
        profileMapper.updateStatus(userId, ACTIVE, null);
        log.info("解除拉黑 userId={}", userId);
    }

    /* ==================== 奖励 ==================== */

    @Override
    public PageResult<UserRewardDto> listRewards(Long userId, int page, int size) {
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 100);
        int offset = (p - 1) * s;
        List<UserRewardDto> items = rewardMapper.listByUser(userId, offset, s).stream()
                .map(this::toRewardDto).toList();
        return PageResult.of(items, rewardMapper.countByUser(userId), p, s);
    }

    @Override
    public Long grantReward(Long userId, String prizeKey, String label, String icon) {
        UserReward r = new UserReward();
        r.setUserId(userId);
        r.setPrizeKey(prizeKey);
        r.setLabel(label);
        r.setIcon(icon);
        r.setStatus("PENDING");
        r.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        rewardMapper.insert(r);
        log.info("发放奖励 userId={} prize={} label={} rewardId={}", userId, prizeKey, label, r.getId());
        return r.getId();
    }

    @Override
    public void updateRewardStatus(Long rewardId, String status, String remark) {
        if (rewardMapper.findById(rewardId) == null) {
            throw RpcBusinessException.of("REWARD_NOT_FOUND", "奖励记录不存在");
        }
        rewardMapper.updateStatus(rewardId, status, remark);
        log.info("更新奖励状态 rewardId={} status={}", rewardId, status);
    }

    /* ==================== 内部方法 ==================== */

    private void ensureProfile(Long userId) {
        if (profileMapper.findById(userId) == null) {
            UserProfile p = new UserProfile();
            p.setUserId(userId);
            p.setStatus(ACTIVE);
            p.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
            profileMapper.upsert(p);
        }
    }

    private UserDto toDto(User u) {
        if (u == null) {
            return null;
        }
        UserDto dto = new UserDto();
        dto.setId(u.getId());
        dto.setUsername(u.getUsername());
        dto.setPoints(u.getPoints());
        dto.setTotalSpins(u.getTotalSpins());
        dto.setTier(tierOf(u.getPoints()));
        dto.setCreatedAt(u.getCreatedAt() == null ? null
                : u.getCreatedAt().toInstant(ZoneOffset.UTC).toEpochMilli());

        UserProfile p = profileMapper.findById(u.getId());
        if (p == null) {
            dto.setStatus(ACTIVE);
            dto.setNickname(u.getUsername());
        } else {
            dto.setStatus(p.getStatus() == null ? ACTIVE : p.getStatus());
            dto.setNickname(p.getNickname() == null ? u.getUsername() : p.getNickname());
            dto.setBlacklistReason(p.getBlacklistReason());
        }
        return dto;
    }

    private UserRewardDto toRewardDto(UserReward r) {
        UserRewardDto dto = new UserRewardDto();
        dto.setId(r.getId());
        dto.setUserId(r.getUserId());
        dto.setPrizeKey(r.getPrizeKey());
        dto.setLabel(r.getLabel());
        dto.setIcon(r.getIcon());
        dto.setStatus(r.getStatus());
        dto.setRemark(r.getRemark());
        dto.setCreatedAt(r.getCreatedAt() == null ? null
                : r.getCreatedAt().toInstant(ZoneOffset.UTC).toEpochMilli());
        return dto;
    }

    /** 与 wheel 端保持一致的等级口径 */
    private static String tierOf(Integer points) {
        int p = points == null ? 0 : points;
        if (p >= 1000) return "钻石";
        if (p >= 500) return "黄金";
        if (p >= 200) return "白银";
        if (p >= 100) return "新人";
        return "青铜";
    }

    private static String normalize(String username) {
        return username == null ? null : username.trim().toLowerCase();
    }

    private static String todayKey() {
        return LocalDate.now(ZoneOffset.UTC).toString();
    }
}
