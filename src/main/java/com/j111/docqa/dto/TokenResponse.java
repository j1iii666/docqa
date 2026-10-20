package com.j111.docqa.dto;

/**
 * 登录成功后返回给客户端的令牌信息。
 *
 * @param token     JWT 字符串，客户端之后放请求头 Authorization: Bearer {token}
 * @param tokenType 固定 "Bearer"
 * @param expiresIn 有效期（秒）
 */
public record TokenResponse(String token, String tokenType, long expiresIn) {

    public static TokenResponse bearer(String token, long expiresIn) {
        return new TokenResponse(token, "Bearer", expiresIn);
    }
}
