package com.j111.docqa.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.j111.docqa.common.Result;
import com.j111.docqa.dto.UserVO;
import com.j111.docqa.security.CurrentUserHolder;
import com.j111.docqa.service.AuthService;

/**
 * 用户相关接口，都需要登录（被 AuthInterceptor 拦截）。
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    /** 拿当前登录用户信息，用来验证 token 鉴权是否生效 */
    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.ok(authService.currentUser(CurrentUserHolder.require()));
    }
}
