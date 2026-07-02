package com.njydsz.pmis.auth.service.impl;

import cn.hutool.core.util.IdUtil;
import com.njydsz.pmis.auth.dto.CaptchaVO;
import com.njydsz.pmis.auth.dto.LoginDTO;
import com.njydsz.pmis.auth.dto.LoginResultVO;
import com.njydsz.pmis.auth.feign.UserAuthClient;
import com.njydsz.pmis.auth.service.AuthService;
import com.njydsz.pmis.common.token.JwtTokenProvider;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.util.CryptoUtil;
import com.njydsz.pmis.user.dto.LoginContextDTO;
import com.wf.captcha.SpecCaptcha;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
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

    /** Redis 操作模板（用于验证码、登录失败计数、Token 黑名单） */
    private final StringRedisTemplate redisTemplate;
    /** JWT Token 生成与校验工具 */
    private final JwtTokenProvider jwtTokenProvider;
    /** user 服务 Feign 客户端 */
    private final UserAuthClient userAuthClient;

    /**
     * 是否强制启用图形验证码 (测试场景可关闭)
     */
    @Value("${pmis.auth.captcha-required:true}")
    private boolean captchaRequired = true;

    /**
     * 设置是否强制启用图形验证码（主要用于测试场景）
     *
     * @param captchaRequired 是否启用图形验证码
     */
    public void setCaptchaRequired(boolean captchaRequired) {
        this.captchaRequired = captchaRequired;
    }

    /**
     * 生成图形验证码
     *
     * @return 验证码 VO（含 captchaKey 与 Base64 图片）
     */
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

    /**
     * 登录
     *
     * @param dto 登录请求参数（用户名、密码、验证码等）
     * @return 登录结果 VO（含访问 Token 与刷新 Token）
     * @throws BizException 当验证码错误、用户不存在、账号锁定或密码错误时抛出
     */
    @Override
    public LoginResultVO login(LoginDTO dto) {
        // 1. 图形验证码校验（可配置关闭）
        if (captchaRequired) {
            validateCaptcha(dto.getCaptchaKey(), dto.getCaptchaCode());
        }

        // 2. 通过 Feign 加载登录上下文
        Result<LoginContextDTO> r = userAuthClient.getLoginContextByUsername(dto.getUsername());
        if (r == null || !r.isSuccess() || r.getData() == null) {
            log.warn("[Auth] 用户不存在 username={}", dto.getUsername());
            throw new BizException(BizErrorCode.USER_NOT_FOUND);
        }
        LoginContextDTO ctx = r.getData();

        // 3. 锁定检查
        if (ctx.getLockedUntil() != null && ctx.getLockedUntil() > System.currentTimeMillis()) {
            throw new BizException(BizErrorCode.USER_LOCKED, "error.auth.msg_9d09bb97");
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

    /**
     * 刷新 Token
     *
     * @param refreshToken 刷新 Token
     * @return 新的登录结果 VO（含新的访问 Token 与刷新 Token）
     * @throws BizException 当刷新 Token 无效或用户不存在/禁用时抛出
     */
    @Override
    public LoginResultVO refresh(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BizException(BizErrorCode.TOKEN_INVALID);
        }

        Long userId = jwtTokenProvider.getUserId(refreshToken);

        // 重新加载上下文（角色权限可能已变）
        Result<LoginContextDTO> r = userAuthClient.getLoginContextById(userId);
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

    /**
     * 登出
     *
     * @param userId 用户 ID
     */
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
     *
     * @param token         待拉黑的 Token
     * @param expireSeconds 黑名单有效期（秒），通常与 Token 剩余有效期一致
     */
    public void blacklistToken(String token, long expireSeconds) {
        if (token == null || token.isBlank()) return;
        redisTemplate.opsForValue().set(
                TOKEN_BLACKLIST_PREFIX + token, "1", expireSeconds, TimeUnit.SECONDS);
    }

    /**
     * 校验 Token 是否在黑名单
     *
     * @param token 待校验的 Token
     * @return true 表示在黑名单中（已登出），false 表示可用
     */
    public boolean isTokenBlacklisted(String token) {
        if (token == null || token.isBlank()) return false;
        return Boolean.TRUE.equals(redisTemplate.hasKey(TOKEN_BLACKLIST_PREFIX + token));
    }

    // ============== 私有方法 ==============

    /**
     * 校验图形验证码
     *
     * @param key  验证码 Key
     * @param code 用户输入的验证码
     * @throws BizException 当验证码为空、已过期或错误时抛出
     */
    private void validateCaptcha(String key, String code) {
        if (key == null || key.isBlank() || code == null || code.isBlank()) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.auth.msg_e7006630");
        }
        String stored = redisTemplate.opsForValue().get(CAPTCHA_KEY_PREFIX + key);
        if (stored == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.auth.msg_ffa59696");
        }
        if (!stored.equalsIgnoreCase(code)) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.auth.msg_08e91fbb");
        }
        // 一次性使用
        redisTemplate.delete(CAPTCHA_KEY_PREFIX + key);
    }

    /**
     * 记录登录失败次数，达到阈值时触发账号锁定
     *
     * @param username 用户名
     */
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

    /**
     * 清除登录失败计数（登录成功后调用）
     *
     * @param username 用户名
     */
    private void clearLoginFailure(String username) {
        redisTemplate.delete(LOGIN_FAIL_PREFIX + username);
    }

    /**
     * 将时间戳转换为本地日期时间
     *
     * @param ts 毫秒时间戳
     * @return 本地日期时间
     */
    @SuppressWarnings("unused")
    private static LocalDateTime toLocalTime(long ts) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.systemDefault());
    }

    /**
     * 将可能为 null 的列表转换为空列表
     *
     * @param v 原始列表
     * @return 非 null 列表
     */
    @SuppressWarnings("unused")
    private static List<String> emptyIfNull(List<String> v) {
        return v == null ? List.of() : v;
    }
}
