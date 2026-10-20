package com.j111.docqa.dto;

import java.time.LocalDateTime;

import com.j111.docqa.entity.User;

/**
 * 对外暴露的用户信息（绝不含 password 字段）。
 */
public record UserVO(Long id, String username, LocalDateTime createdAt) {

    public static UserVO from(User user) {
        return new UserVO(user.getId(), user.getUsername(), user.getCreatedAt());
    }
}
