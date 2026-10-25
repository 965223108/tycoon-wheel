package com.tycoon.service;

import com.tycoon.api.dto.UserDto;
import com.tycoon.api.exception.RpcBusinessException;
import com.tycoon.api.user.UserDubboService;
import com.tycoon.config.GameProperties;
import com.tycoon.domain.SessionToken;
import com.tycoon.mapper.SessionMapper;
import com.tycoon.web.ApiException;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 账号与会话。
 *
 * <p>账号数据全部通过 <b>Dubbo</b> 交给 user-service 处理；本服务只负责
 * 自己的 sessions 表（会话令牌）与 Cookie。
 */
@Service
public class AuthService {

    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_\\u4e00-\\u9fa5]{2,16}$");
    private static final SecureRandom RANDOM = new SecureRandom();

    @DubboReference(interfaceClass = UserDubboService.class,
            check = false, timeout = 5000, retries = 0)
    private UserDubboService userService;

    private final SessionMapper sessions;
    private final GameProperties props;

    public AuthService(SessionMapper sessions, GameProperties props) {
        this.sessions = sessions;
        this.props = props;
    }

    public record Issued(UserDto user, String token) {
    }

    public Issued register(String rawUsername, String password) {
        String username = normalize(rawUsername);
        validate(username, password);
        UserDto user;
        try {
            user = userService.register(username, password, props.getInitialPoints());
        } catch (RpcBusinessException e) {
            if ("USERNAME_TAKEN".equals(e.getCode())) {
                throw ApiException.conflict("USERNAME_TAKEN", "这个账号已经被注册了");
            }
            throw ApiException.badRequest(e.getCode(), e.getMessage());
        } catch (Exception e) {
            throw ApiException.badRequest("USER_SERVICE_UNAVAILABLE", "用户服务暂时不可用，请稍后再试");
        }
        return new Issued(user, issue(user.getId()));
    }

    public Issued login(String rawUsername, String password) {
        UserDto user;
        try {
            user = userService.authenticate(normalize(rawUsername), password);
        } catch (Exception e) {
            throw ApiException.badRequest("USER_SERVICE_UNAVAILABLE", "用户服务暂时不可用，请稍后再试");
        }
        if (user == null) {
            throw ApiException.unauthorized("账号或密码不正确");
        }
        return new Issued(user, issue(user.getId()));
    }

    /** 从 Cookie 里的令牌解析出当前账号；无效则抛 401 */
    public UserDto resolve(String token) {
        if (token == null || token.isBlank()) {
            throw ApiException.unauthorized("请先登录");
        }
        SessionToken session = sessions.findValid(token, Instant.now());
        if (session == null) {
            throw ApiException.unauthorized("登录已过期，请重新登录");
        }
        UserDto user;
        try {
            user = userService.findById(session.getUserId());
        } catch (Exception e) {
            throw ApiException.badRequest("USER_SERVICE_UNAVAILABLE", "用户服务暂时不可用，请稍后再试");
        }
        if (user == null) {
            throw ApiException.unauthorized("账号不存在");
        }
        if ("BLACKLISTED".equals(user.getStatus())) {
            throw ApiException.forbidden("ACCOUNT_BLOCKED",
                    user.getBlacklistReason() == null ? "账号已被限制，请联系客服" : user.getBlacklistReason());
        }
        return user;
    }

    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            sessions.deleteByToken(token);
        }
    }

    private String issue(Long userId) {
        sessions.deleteExpired(Instant.now());
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = HexFormat.of().formatHex(raw);
        sessions.insert(new SessionToken(token, userId,
                Instant.now().plus(props.getSessionTtlHours(), ChronoUnit.HOURS)));
        return token;
    }

    private static String normalize(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    private static void validate(String username, String password) {
        if (!USERNAME.matcher(username).matches()) {
            throw ApiException.badRequest("BAD_USERNAME", "账号需为 2–16 位字母、数字、下划线或中文");
        }
        if (password == null || password.length() < 4 || password.length() > 64) {
            throw ApiException.badRequest("BAD_PASSWORD", "密码需为 4–64 位");
        }
    }
}
