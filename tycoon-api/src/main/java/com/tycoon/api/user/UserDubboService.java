package com.tycoon.api.user;

import com.tycoon.api.dto.PageResult;
import com.tycoon.api.dto.UserDto;
import com.tycoon.api.dto.UserRewardDto;

/**
 * 用户领域 Dubbo 服务（提供者：user-service）。
 *
 * <p>wheel-service 通过它完成注册/登录校验、扣分、发奖、拉黑校验；
 * admin-service 通过它完成用户管理、奖励管理、拉黑操作。
 */
public interface UserDubboService {

    /* ==================== 查询 ==================== */

    UserDto findById(Long userId);

    UserDto findByUsername(String username);

    PageResult<UserDto> listUsers(String keyword, int page, int size);

    /** 是否在黑名单（被拉黑的用户不能抽奖） */
    boolean isBlacklisted(Long userId);

    /* ==================== 账号 ==================== */

    /**
     * 注册，返回用户信息。用户名重复时抛 RpcBusinessException("USERNAME_TAKEN")。
     */
    UserDto register(String username, String rawPassword, int initialPoints);

    /**
     * 校验账号密码。成功返回用户，失败返回 null。
     */
    UserDto authenticate(String username, String rawPassword);

    /* ==================== 积分 ==================== */

    /**
     * 原子扣分。积分不足返回 false，不抛异常。
     *
     * @param cost        扣多少分
     * @param spinsDelta  同时累加抽奖次数（通常是 1）
     */
    boolean deductPoints(Long userId, int cost, int spinsDelta);

    /** 加/减积分（运营发放或扣回），返回最新积分 */
    int addPoints(Long userId, int delta);

    /* ==================== 黑名单 ==================== */

    void blacklist(Long userId, String reason);

    void unblacklist(Long userId);

    /* ==================== 奖励 ==================== */

    PageResult<UserRewardDto> listRewards(Long userId, int page, int size);

    /** 发放奖励，返回奖励记录 id */
    Long grantReward(Long userId, String prizeKey, String label, String icon);

    void updateRewardStatus(Long rewardId, String status, String remark);
}
