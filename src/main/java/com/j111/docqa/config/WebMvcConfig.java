package com.j111.docqa.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.j111.docqa.ratelimit.RateLimitInterceptor;
import com.j111.docqa.security.AuthInterceptor;

/**
 * Spring MVC 配置。
 * <p>
 * 拦截器顺序很重要：先鉴权（拿到 userId），再限流（按 userId 计数）。
 * 注册顺序即执行顺序。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;

    public WebMvcConfig(AuthInterceptor authInterceptor, RateLimitInterceptor rateLimitInterceptor) {
        this.authInterceptor = authInterceptor;
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                // 默认全部接口都要登录（fail-closed），只显式放行注册登录和心跳
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/**", "/api/health")
                .order(1);

        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**")
                .order(2);
    }
}
