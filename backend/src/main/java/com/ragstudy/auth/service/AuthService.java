package com.ragstudy.auth.service;

import com.ragstudy.auth.convert.AuthConvert;
import com.ragstudy.auth.controller.dto.AuthRequests.LoginRequest;
import com.ragstudy.auth.controller.dto.AuthRequests.RegisterRequest;
import com.ragstudy.auth.controller.dto.AuthResponse;
import com.ragstudy.auth.dal.dataobject.AuthTokenEntity;
import com.ragstudy.auth.dal.dataobject.UserEntity;
import com.ragstudy.auth.dal.repository.AuthTokenRepository;
import com.ragstudy.auth.dal.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final AuthTokenRepository authTokenRepository;
    private final PasswordHasher passwordHasher;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            UserRepository userRepository,
            AuthTokenRepository authTokenRepository,
            PasswordHasher passwordHasher
    ) {
        this.userRepository = userRepository;
        this.authTokenRepository = authTokenRepository;
        this.passwordHasher = passwordHasher;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase();

        if (userRepository.existsByUsername(username)) {
            throw new AuthException(HttpStatus.CONFLICT, "用户名已存在");
        }

        if (userRepository.existsByEmail(email)) {
            throw new AuthException(HttpStatus.CONFLICT, "邮箱已存在");
        }

        LocalDateTime now = LocalDateTime.now();
        UserEntity user = new UserEntity(
                UUID.randomUUID().toString(),
                username,
                email,
                passwordHasher.hash(request.password()),
                normalizeNickname(request.nickname(), username),
                now,
                now
        );

        UserEntity savedUser = userRepository.save(user);

        return createAuthResponse(savedUser);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String account = request.account().trim();
        UserEntity user = userRepository.findByUsernameOrEmail(account, account.toLowerCase())
                .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "账号或密码错误"));

        if (!passwordHasher.matches(request.password(), user.getPasswordHash())) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "账号或密码错误");
        }

        return createAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public UserEntity requireUser(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "请先登录");
        }

        String token = authorizationHeader.substring("Bearer ".length()).trim();
        AuthTokenEntity authToken = authTokenRepository.findById(token)
                .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "登录已失效"));

        if (authToken.isExpired()) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "登录已过期");
        }

        return userRepository.findById(authToken.getUserId())
                .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "用户不存在"));
    }

    private AuthResponse createAuthResponse(UserEntity user) {
        String token = createToken();
        LocalDateTime now = LocalDateTime.now();
        authTokenRepository.save(new AuthTokenEntity(token, user.getId(), now, now.plusDays(30)));

        return new AuthResponse(token, AuthConvert.toDto(user));
    }

    private String createToken() {
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    private String normalizeNickname(String nickname, String username) {
        if (nickname == null || nickname.isBlank()) {
            return username;
        }

        return nickname.trim();
    }
}
