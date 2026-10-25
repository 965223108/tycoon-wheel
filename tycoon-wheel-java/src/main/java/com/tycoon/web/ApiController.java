package com.tycoon.web;

import com.tycoon.api.dto.UserDto;
import com.tycoon.config.GameProperties;
import com.tycoon.domain.SpinRecord;
import com.tycoon.game.Segment;
import com.tycoon.game.WheelCatalog;
import com.tycoon.mapper.SpinRecordMapper;
import com.tycoon.mapper.UserMapper;
import com.tycoon.service.AuthService;
import com.tycoon.service.WheelService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ApiController {

    private static final String SID = "TYCOON_SID";

    private final AuthService auth;
    private final WheelService wheel;
    private final WheelCatalog catalog;
    private final UserMapper userMapper;
    private final SpinRecordMapper recordMapper;
    private final GameProperties props;

    public ApiController(AuthService auth, WheelService wheel, WheelCatalog catalog,
                         UserMapper userMapper, SpinRecordMapper recordMapper, GameProperties props) {
        this.auth = auth;
        this.wheel = wheel;
        this.catalog = catalog;
        this.userMapper = userMapper;
        this.recordMapper = recordMapper;
        this.props = props;
    }

    /* ==================== 公共配置 ==================== */

    @GetMapping("/config")
    public Dto.ConfigView config() {
        List<Dto.SegmentView> segs = catalog.segments().stream()
                .map(s -> new Dto.SegmentView(s.index(), s.key(), s.label(), s.icon(),
                        s.type(), s.desc(), round1(catalog.percentOf(s))))
                .toList();
        return new Dto.ConfigView(props.getCostPerSpin(), props.getInitialPoints(),
                props.getDailyLimit(), segs);
    }

    /* ==================== 账号 ==================== */

    @PostMapping("/register")
    public Dto.MeView register(@RequestBody Dto.CredentialsReq req, HttpServletResponse res) {
        AuthService.Issued issued = auth.register(req.username(), req.password());
        writeCookie(res, issued.token());
        return me(issued.user());
    }

    @PostMapping("/login")
    public Dto.MeView login(@RequestBody Dto.CredentialsReq req, HttpServletResponse res) {
        AuthService.Issued issued = auth.login(req.username(), req.password());
        writeCookie(res, issued.token());
        return me(issued.user());
    }

    @PostMapping("/logout")
    public Dto.ApiError logout(@CookieValue(name = SID, required = false) String sid,
                               HttpServletResponse res) {
        auth.logout(sid);
        clearCookie(res);
        return new Dto.ApiError("OK", "已退出");
    }

    /* ==================== 游戏 ==================== */

    @GetMapping("/me")
    public Dto.MeView me(@CookieValue(name = SID, required = false) String sid) {
        return me(auth.resolve(sid));
    }

    @PostMapping("/spin")
    public Dto.SpinResult spin(@CookieValue(name = SID, required = false) String sid) {
        UserDto user = auth.resolve(sid);
        WheelService.Outcome out = wheel.spin(user);
        Segment s = out.segment();

        Dto.SegmentView view = new Dto.SegmentView(s.index(), s.key(), s.label(), s.icon(),
                s.type(), s.desc(), round1(catalog.percentOf(s)));

        return new Dto.SpinResult(
                s.index(), view, s.desc(), out.pointsDelta(), out.points(),
                spinsLeft(out.points()), out.totalSpins(), historyOf(user));
    }

    /* ==================== 运营视角 ==================== */

    @GetMapping("/stats")
    public Dto.StatsView stats() {
        return new Dto.StatsView(
                userMapper.countAll(),
                recordMapper.countAll(),
                recordMapper.countByType("prize"),
                recordMapper.countByType("coupon"),
                recordMapper.sumPointsAwarded());
    }

    /** 供网关/运维探活，不走会话 */
    @GetMapping("/instance")
    public Dto.InstanceView instance() {
        return new Dto.InstanceView(
                System.getenv().getOrDefault("HOSTNAME", "unknown"),
                System.getProperty("server.port", "8787"));
    }

    /* ==================== 内部工具 ==================== */

    private Dto.MeView me(UserDto user) {
        return new Dto.MeView(
                user.getUsername(),
                user.getPoints(),
                user.getTotalSpins(),
                spinsLeft(user.getPoints()),
                tierOf(user.getPoints()),
                user.getCreatedAt() == null ? 0L : user.getCreatedAt(),
                historyOf(user));
    }

    private List<Dto.HistoryItem> historyOf(UserDto user) {
        return wheel.history(user).stream().map(this::toItem).toList();
    }

    private Dto.HistoryItem toItem(SpinRecord r) {
        return new Dto.HistoryItem(r.getLabel(), r.getIcon(), r.getType(),
                r.getPointsDelta(), r.getCreatedAt().toEpochMilli());
    }

    private int spinsLeft(Integer points) {
        int cost = Math.max(1, props.getCostPerSpin());
        return (points == null ? 0 : points) / cost;
    }

    private static String tierOf(Integer points) {
        int p = points == null ? 0 : points;
        if (p >= 1000) return "钻石";
        if (p >= 500) return "黄金";
        if (p >= 200) return "白银";
        if (p >= 100) return "新人";
        return "青铜";
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private void writeCookie(HttpServletResponse res, String token) {
        ResponseCookie cookie = ResponseCookie.from(SID, token)
                .httpOnly(true)
                .path("/")
                .sameSite("Lax")
                .maxAge(Duration.ofHours(props.getSessionTtlHours()))
                .build();
        res.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearCookie(HttpServletResponse res) {
        ResponseCookie cookie = ResponseCookie.from(SID, "")
                .httpOnly(true)
                .path("/")
                .sameSite("Lax")
                .maxAge(Duration.ZERO)
                .build();
        res.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
