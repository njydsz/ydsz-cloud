package com.njydsz.userinfo.web.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.userinfo.server.auth.AuthService;
import com.njydsz.userinfo.server.auth.LoginResult;

import lombok.Data;
import lombok.RequiredArgsConstructor;

/**
 * Auth Controller - login/logout/refresh.
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public LoginResult login(@RequestBody LoginRequest request) {
        return authService.login(request.getUsername(), request.getPassword());
    }

    @PostMapping("/logout")
    public void logout(@RequestHeader("Authorization") String token) {
        String accessToken = token.startsWith("Bearer ") ? token.substring(7) : token;
        authService.logout(accessToken);
    }

    @PostMapping("/refresh")
    public LoginResult refresh(@RequestBody RefreshRequest request) {
        return authService.refresh(request.getRefreshToken());
    }

    @Data
    public static class LoginRequest {
        private String username;
        private String password;
    }

    @Data
    public static class RefreshRequest {
        private String refreshToken;
    }
}
