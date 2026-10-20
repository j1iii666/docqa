package com.j111.docqa.security;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 登录拦截器：解析请求头 Authorization: Bearer {token}，校验通过就把 userId 放进 ThreadLocal。
 * <p>
 * 这里手写而不是上 Spring Security，是为了让你能讲清楚"JWT 鉴权到底做了什么"；
 * 后面要加角色权限时再换成 Security 也不迟。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuthInterceptor.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public AuthInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return reject(response, "缺少 Authorization 请求头，或格式不是 Bearer {token}");
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        try {
            Long userId = Long.valueOf(jwtService.parse(token).getSubject());
            CurrentUserHolder.set(userId);
            return true;
        } catch (Exception e) {
            // 过期、签名不对、被篡改都走这里，统一按未登录处理，不区分细节（避免给攻击者提示）
            log.debug("token 校验失败: {}", e.getMessage());
            return reject(response, "登录已过期，请重新登录");
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        CurrentUserHolder.clear();
    }

    /** 直接写回统一格式的 401，不走 controller 也不抛异常 */
    private boolean reject(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpStatus.OK.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String body = "{\"code\":401,\"message\":\"" + message + "\",\"data\":null}";
        response.getWriter().write(body);
        return false;
    }
}
