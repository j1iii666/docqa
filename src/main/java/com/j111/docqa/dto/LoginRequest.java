package com.j111.docqa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 登录请求。
 */
public record LoginRequest(

        @NotBlank(message = "用户名不能为空")
        @Size(min = 4, max = 20, message = "用户名长度必须是 4-20 个字符")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 32, message = "密码长度必须是 6-32 个字符")
        String password) {
}
