package com.j111.docqa.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.j111.docqa.common.Result;
import com.j111.docqa.dto.LoginRequest;
import com.j111.docqa.dto.RegisterRequest;
import com.j111.docqa.dto.TokenResponse;
import com.j111.docqa.dto.UserVO;
import com.j111.docqa.service.AuthService;

import jakarta.validation.Valid;

/**
 * 认证接口：注册、登录。这两个接口不需要 token。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public Result<UserVO> register(@Valid @RequestBody RegisterRequest request) {
        return Result.ok(authService.register(request));
    }

    @PostMapping("/login")
    public Result<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }
}
