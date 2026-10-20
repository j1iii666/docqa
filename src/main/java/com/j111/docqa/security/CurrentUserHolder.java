package com.j111.docqa.security;

/**
 * 保存"当前请求的登录用户 id"。
 * <p>
 * 用 ThreadLocal 存：一次请求由一个 Tomcat 线程处理贯穿始终，
 * 所以能在 service/controller 任意位置拿到当前用户，而不用层层传参。
 * 必须在请求结束时 clear()，否则线程复用会把上一个请求的用户带过来（内存泄漏 + 串号）。
 */
public final class CurrentUserHolder {

    private static final ThreadLocal<Long> HOLDER = new ThreadLocal<>();

    private CurrentUserHolder() {
    }

    public static void set(Long userId) {
        HOLDER.set(userId);
    }

    public static Long get() {
        return HOLDER.get();
    }

    public static Long require() {
        Long userId = HOLDER.get();
        if (userId == null) {
            throw new IllegalStateException("当前请求没有登录用户");
        }
        return userId;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
