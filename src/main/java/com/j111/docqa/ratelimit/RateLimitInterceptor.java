package com.j111.docqa.ratelimit;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.j111.docqa.security.CurrentUserHolder;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 读方法上的 {@link RateLimit} 注解并执行限流。
 * <p>
 * 必须排在鉴权拦截器之后（WebMvcConfig 里的注册顺序决定），
 * 因为按用户限流需要 CurrentUserHolder 里已经有 userId。
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);

    private final RateLimitService rateLimitService;

    public RateLimitInterceptor(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        // SSE（SseEmitter）/ DeferredResult 这类异步请求会经历两次分发：
        // 一次是正常请求，一次是异步处理完成后重新分发（DispatcherType.ASYNC）。
        // 不排除 ASYNC，拦截器会跑两次，等于每个请求扣两个令牌——限流额度直接少一半。
        if (request.getDispatcherType() == DispatcherType.ASYNC) {
            return true;
        }

        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RateLimit rateLimit = handlerMethod.getMethodAnnotation(RateLimit.class);
        if (rateLimit == null) {
            return true;
        }

        String dimension = switch (rateLimit.key()) {
            case USER -> {
                Long userId = CurrentUserHolder.get();
                yield userId == null ? "ip:" + clientIp(request) : "user:" + userId;
            }
            case IP -> "ip:" + clientIp(request);
        };
        String bucketKey = dimension + ":" + request.getMethod() + ":" + request.getRequestURI();

        if (rateLimitService.tryAcquire(bucketKey, rateLimit.capacity(), rateLimit.perSeconds())) {
            return true;
        }

        log.info("触发限流: {}", bucketKey);
        reject(response);
        return false;
    }

    private void reject(HttpServletResponse response) throws Exception {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(
                "{\"code\":429,\"message\":\"请求太频繁，请稍后再试\",\"data\":null}");
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // 经过代理时取第一个，即真实客户端
            return forwarded.split(",")[0].strip();
        }
        return request.getRemoteAddr();
    }
}
