package com.tycoon.admin.web;

import com.tycoon.admin.domain.AdminUser;
import com.tycoon.admin.mapper.AdminUserMapper;
import com.tycoon.admin.security.JwtService;
import com.tycoon.admin.util.PasswordHasher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 后台登录：校验账号密码后签发 JWT，后续请求由网关 + AdminAuthFilter 校验。
 */
@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final AdminUserMapper adminUserMapper;
    private final JwtService jwtService;

    public AdminAuthController(AdminUserMapper adminUserMapper, JwtService jwtService) {
        this.adminUserMapper = adminUserMapper;
        this.jwtService = jwtService;
    }

    public record LoginRequest(String username, String password) {
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        if (req == null || req.username() == null || req.password() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "BAD_REQUEST", "message", "请输入账号和密码"));
        }
        AdminUser u = adminUserMapper.findByUsername(req.username().trim());
        if (u == null || !PasswordHasher.verify(req.password(), u.getSalt(), u.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "BAD_CREDENTIALS", "message", "账号或密码不正确"));
        }
        String token = jwtService.issue(u.getUsername(), u.getRole());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("token", token);
        body.put("username", u.getUsername());
        body.put("role", u.getRole());
        body.put("expiresInHours", jwtService.getTtlHours());
        return ResponseEntity.ok(body);
    }

    /** 回显当前令牌身份，用于前端判断登录态 */
    @GetMapping("/me")
    public ResponseEntity<?> me(HttpServletRequest request) {
        return ResponseEntity.ok(Map.of(
                "username", String.valueOf(request.getAttribute("adminUser")),
                "role", String.valueOf(request.getAttribute("adminRole"))
        ));
    }
}
