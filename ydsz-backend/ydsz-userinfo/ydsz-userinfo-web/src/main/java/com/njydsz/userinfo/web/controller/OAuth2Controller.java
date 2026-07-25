package com.njydsz.userinfo.web.controller;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.redis.service.ops.RedisStringOps;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * OAuth2 Authorization Code Grant flow.
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/oauth2")
@RequiredArgsConstructor
@Tag(name = "OAuth2", description = "OAuth2 authorization code flow")
public class OAuth2Controller {

    private final RedisStringOps redisStringOps;

    private static final long CODE_TTL_SECONDS = 300;
    private static final String CODE_KEY_PREFIX = "oauth2:code:";

    @GetMapping("/authorize")
    @Operation(summary = "Get authorization code")
    public String authorize(
            @RequestParam String clientId,
            @RequestParam String redirectUri,
            @RequestParam(required = false) String state) {
        String code = UUID.randomUUID().toString().replace("-", "");
        redisStringOps.set(CODE_KEY_PREFIX + code, clientId, CODE_TTL_SECONDS, TimeUnit.SECONDS);
        log.info("OAuth2 authorize: clientId={}, code={}", clientId, code);
        return code;
    }

    @GetMapping("/token")
    @Operation(summary = "Exchange code for token")
    public Object token(
            @RequestParam String code,
            @RequestParam String clientId) {
        String storedClientId = redisStringOps.get(CODE_KEY_PREFIX + code, String.class);
        if (storedClientId == null || !storedClientId.equals(clientId)) {
            throw new RuntimeException("Invalid or expired authorization code");
        }
        redisStringOps.delete(CODE_KEY_PREFIX + code);
        log.info("OAuth2 token: clientId={}, code={}", clientId, code);
        return Map.of("message", "Token issued", "code", code);
    }
}
