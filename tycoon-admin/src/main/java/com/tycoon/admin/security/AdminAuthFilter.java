package com.tycoon.admin.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 后台接口的兜底鉴权。
 *
 * <p>正常流量由网关的 Spring Security 拦截并校验 JWT；这里再校验一次，
 * 避免有人绕过网关直连 admin-service（纵深防御）。
 */
@Component
public class AdminAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public AdminAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        // 登录接口 / 健康检查 / 静态页面 / 监控端点放行
        return !uri.startsWith("/api/admin")
                || uri.startsWith("/api/admin/auth/login")
                || uri.startsWith("/api/admin/health")
                || uri.startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
        Claims claims = jwtService.parse(auth);
        if (claims == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"error\":\"UNAUTHORIZED\",\"message\":\"缺少或无效的管理员令牌\"}");
            return;
        }
        request.setAttribute("adminUser", claims.getSubject());
        request.setAttribute("adminRole", claims.get("role"));
        chain.doFilter(request, response);
    }
}
