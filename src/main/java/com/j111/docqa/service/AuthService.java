package com.j111.docqa.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.j111.docqa.common.BizException;
import com.j111.docqa.common.ResultCode;
import com.j111.docqa.dto.LoginRequest;
import com.j111.docqa.dto.RegisterRequest;
import com.j111.docqa.dto.TokenResponse;
import com.j111.docqa.dto.UserVO;
import com.j111.docqa.entity.User;
import com.j111.docqa.repository.UserRepository;
import com.j111.docqa.security.JwtService;

/**
 * 注册 / 登录。
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public UserVO register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new BizException(ResultCode.USERNAME_TAKEN);
        }

        User user = new User();
        user.setUsername(request.username());
        // 存哈希，不存明文；BCrypt 每次加密结果都不同（自带随机盐）
        user.setPassword(passwordEncoder.encode(request.password()));
        return UserVO.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                // 用户不存在和密码错误返回同一个提示，避免被用来探测哪些用户名已注册
                .orElseThrow(() -> new BizException(ResultCode.LOGIN_FAILED));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BizException(ResultCode.LOGIN_FAILED);
        }

        String token = jwtService.issue(user);
        return TokenResponse.bearer(token, jwtService.getExpireSeconds());
    }

    @Transactional(readOnly = true)
    public UserVO currentUser(Long userId) {
        return userRepository.findById(userId)
                .map(UserVO::from)
                .orElseThrow(() -> new BizException(ResultCode.UNAUTHORIZED));
    }
}
