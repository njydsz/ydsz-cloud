package com.njydsz.pmis.auth.service.impl;

import cn.hutool.core.util.IdUtil;
import com.njydsz.pmis.auth.dto.CaptchaVO;
import com.njydsz.pmis.auth.dto.LoginDTO;
import com.njydsz.pmis.auth.dto.LoginResultVO;
import com.njydsz.pmis.auth.feign.UserAuthClient;
import com.njydsz.pmis.auth.service.AuthService;
import com.njydsz.pmis.common.token.JwtTokenProvider;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.util.CryptoUtil;
import com.njydsz.pmis.user.dto.LoginContextDTO;
import com.wf.captcha.SpecCaptcha;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 认证服务实现
 *
 * <p>核心流程：
 * <ol>
 *   <li>校验图形验证码（Redis 5 分钟有效期）</li>
 *   <li>通过 Feign 调 user 服务加载登录上下文</li>
 *   <li>校验密码（MD5 + 随机盐）</li>
 *   <li>校验用户状态（ENABLED / 锁定）</li>
 *   <li>生成 JWT（roles/permissions 写入 Claims）</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** 验证码 Redis Key 前缀 */
    private static final String CAPTCHA_KEY_PREFIX = "pmis:captcha:";
    /** 登录失败计数 Redis Key 前缀 */
    private static final String LOGIN_FAIL_PREFIX = "pmis:login:fail:";
    /** Token 黑名单 Redis Key 前缀 */
    private static final String TOKEN_BLACKLIST_PREFIX = "pmis:token:blacklist:";

    /** 验证码有效期(分钟) */
    private static final long CAPTCHA_EXPIRE_MINUTES = 5;
    /** 访问 Token 有效期(小时) */
    private static final long TOKEN_EXPIRE_HOURS = 8;
    /** 刷新 Token 有效期(天) */
    private static final long REFRESH_TOKEN_EXPIRE_DAYS = 7;
    /** 登录失败锁定阈值(次) */
    private static final int LOGIN_FAIL_THRESHOLD = 5;
    /** 登录锁定时长(分钟) */
    private static final long LOGIN_LOCK_MINUTES = 30;

    private final StringRedisTemplate redisTemplate;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserAuthClient userAuthClient;

    /**
     * 是否强制启用图形验证码 (测试场景可关闭)
     */
    @Value("${pmis.auth.captcha-required:true}")
    private boolean captchaRequired = true;

    public void setCaptchaRequired(boolean captchaRequired) {
        this.captchaRequired = captchaRequired;
    }

    @Override
    public CaptchaVO generateCaptcha() {
        // 1. 生成图形验证码 (使用 easy-captcha)
        SpecCaptcha captcha = new SpecCaptcha(130, 48, 4);
        captcha.setCharType(SpecCaptcha.TYPE_DEFAULT);
        String code = captcha.text().toLowerCase();
        String image = captcha.toBase64();

        // 2. 写入 Redis (5 分钟过期)
        String key = IdUtil.fastSimpleUUID();
        redisTemplate.opsForValue().set(CAPTCHA_KEY_PREFIX + key, code, CAPTCHA_EXPIRE_MINUTES, TimeUnit.MINUTES);

        return CaptchaVO.builder()
                .captchaKey(key)
                .captchaImage(image)
                .build();
    }

    @Override
    public LoginResultVO login(LoginDTO dto) {
        // 1. 图形验证码校验（可配置关闭）
        if (captchaRequired) {
            validateCaptcha(dto.getCaptchaKey(), dto.getCaptchaCode());
        }

        // 2. 通过 Feign 加载登录上下文
        R<LoginContextDTO> r = userAuthClient.getLoginContextByUsername(dto.getUsername());
        if (r == null || !r.isSuccess() || r.getData() == null) {
            log.warn("[Auth] 用户不存在 username={}", dto.getUsername());
            throw new BizException(BizErrorCode.USER_NOT_FOUND);
        }
        LoginContextDTO ctx = r.getData();

        // 3. 锁定检查
        if (ctx.getLockedUntil() != null && ctx.getLockedUntil() > System.currentTimeMillis()) {
            throw new BizException(BizErrorCode.USER_LOCKED, "账号已锁定,请稍后再试");
        }

        // 4. 状态校验
        if (!"ENABLED".equalsIgnoreCase(ctx.getStatus())) {
            throw new BizException(BizErrorCode.USER_DISABLED);
        }

        // 5. 密码校验
        if (!CryptoUtil.verifyPassword(dto.getPassword(), ctx.getPassword(), ctx.getSalt())) {
            recordLoginFailure(dto.getUsername());
            throw new BizException(BizErrorCode.PASSWORD_INCORRECT);
        }

        // 6. 清除失败计数
        clearLoginFailure(dto.getUsername());

        // 7. 生成 Token (roles/permissions 写入 Claims)
        String token = jwtTokenProvider.generateToken(
                ctx.getUserId(), ctx.getUsername(),
                ctx.getRoles(), ctx.getPermissions(),
                TOKEN_EXPIRE_HOURS * 3600L);
        String refreshToken = jwtTokenProvider.generateRefreshToken(
                ctx.getUserId(), REFRESH_TOKEN_EXPIRE_DAYS * 24 * 3600L);

        log.info("[Auth] 登录成功 userId={} username={} roles={}",
                ctx.getUserId(), ctx.getUsername(), ctx.getRoles());

        return LoginResultVO.builder()
                .token(token)
                .refreshToken(refreshToken)
                .expiresIn(TOKEN_EXPIRE_HOURS * 3600L)
                .build();
    }

    @Override
    public LoginResultVO refresh(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BizException(BizErrorCode.TOKEN_INVALID);
        }

        Long userId = jwtTokenProvider.getUserId(refreshToken);

        // 重新加载上下文（角色权限可能已变）
        R<LoginContextDTO> r = userAuthClient.getLoginContextById(userId);
        if (r == null || !r.isSuccess() || r.getData() == null) {
            throw new BizException(BizErrorCode.USER_NOT_FOUND);
        }
        LoginContextDTO ctx = r.getData();
        if (!"ENABLED".equalsIgnoreCase(ctx.getStatus())) {
            throw new BizException(BizErrorCode.USER_DISABLED);
        }

        String newToken = jwtTokenProvider.generateToken(
                ctx.getUserId(), ctx.getUsername(),
                ctx.getRoles(), ctx.getPermissions(),
                TOKEN_EXPIRE_HOURS * 3600L);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(
                ctx.getUserId(), REFRESH_TOKEN_EXPIRE_DAYS * 24 * 3600L);

        return LoginResultVO.builder()
                .token(newToken)
                .refreshToken(newRefreshToken)
                .expiresIn(TOKEN_EXPIRE_HOURS * 3600L)
                .build();
    }

    @Override
    public void logout(String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        // 将当前 Token 加入黑名单 (由 AuthGlobalFilter 检查)
        // 注: 实际项目中应通过请求头拿 Token 一起加入黑名单
        log.info("[Auth] 登出 userId={}", userId);
    }

    /**
     * 将 Token 加入黑名单
     */
    public void blacklistToken(String token, long expireSeconds) {
        if (token == null || token.isBlank()) return;
        redisTemplate.opsForValue().set(
                TOKEN_BLACKLIST_PREFIX + token, "1", expireSeconds, TimeUnit.SECONDS);
    }

    /**
     * 校验 Token 是否在黑名单
     */
    public boolean isTokenBlacklisted(String token) {
        if (token == null || token.isBlank()) return false;
        return Boolean.TRUE.equals(redisTemplate.hasKey(TOKEN_BLACKLIST_PREFIX + token));
    }

    // ============== 私有方法 ==============

    private void validateCaptcha(String key, String code) {
        if (key == null || key.isBlank() || code == null || code.isBlank()) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "请输入图形验证码");
        }
        String stored = redisTemplate.opsForValue().get(CAPTCHA_KEY_PREFIX + key);
        if (stored == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "验证码已过期,请刷新");
        }
        if (!stored.equalsIgnoreCase(code)) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "验证码错误");
        }
        // 一次性使用
        redisTemplate.delete(CAPTCHA_KEY_PREFIX + key);
    }

    private void recordLoginFailure(String username) {
        String key = LOGIN_FAIL_PREFIX + username;
        Long count = redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, LOGIN_LOCK_MINUTES, TimeUnit.MINUTES);
        if (count != null && count >= LOGIN_FAIL_THRESHOLD) {
            log.warn("[Auth] 账号 {} 登录失败 {} 次,触发锁定", username, count);
            // 锁定账号: 通过调用 user 服务更新 locked_until
            // 此处简化处理, 实际生产可异步推送 user 服务
        }
    }

    private void clearLoginFailure(String username) {
        redisTemplate.delete(LOGIN_FAIL_PREFIX + username);
    }

    @SuppressWarnings("unused")
    private static LocalDateTime toLocalTime(long ts) {
        return LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(ts), ZoneId.systemDefault());
    }

    @SuppressWarnings("unused")
    private static List<String> emptyIfNull(List<String> v) {
        return v == null ? List.of() : v;
    }
}
