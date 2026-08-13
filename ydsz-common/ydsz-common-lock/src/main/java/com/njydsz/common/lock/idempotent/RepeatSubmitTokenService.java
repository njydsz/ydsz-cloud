package com.njydsz.common.lock.idempotent;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.lock.annotation.RepeatSubmit;
import com.njydsz.common.security.LoginUser;


/**
 * 表单重复提交 Token 服务
 *
 * <p>提供 Token 的生成、校验和删除功能，用于防止表单重复提交。
 * Token 存储在 Redis 中，与用户 ID 绑定，一次性使用。
 *
 * <p><b>工作流程：</b>
 * <ol>
 *   <li>前端调用 {@link #generateToken()} 获取 Token</li>
 *   <li>前端提交表单时携带 Token（通过 HTTP 请求头）</li>
 *   <li>后端调用 {@link #validateAndConsume(String)} 校验并消费 Token</li>
 *   <li>校验成功后 Token 自动删除，防止重复使用</li>
 * </ol>
 *
 * <p><b>Redis Key 格式：</b>
 * {@code ydsz:repeat:token:{userId}:{token}}
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see RepeatSubmit
 */
@Slf4j
public class RepeatSubmitTokenService {

    private static final String TOKEN_PREFIX = "ydsz:repeat:token:";
    private static final String TOKEN_VALUE = "1";
    private static final String INTERVAL_PREFIX = "ydsz:repeat:interval:";

    private final StringRedisTemplate redisTemplate;

    public RepeatSubmitTokenService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 生成防重复提交 Token
     *
     * <p>为当前登录用户生成一个一次性 Token，有效期由调用方指定。
     * Token 与用户 ID 绑定，确保同一用户只能使用自己生成的 Token。
     *
     * @param ttlMillis Token 有效期（毫秒）
     * @return 生成的 Token 字符串
     * @throws IllegalStateException 当前用户未登录时抛出
     */
    public String generateToken(long ttlMillis) {
        String userId = getCurrentUserId();
        if (!StringUtils.hasText(userId)) {
            throw new IllegalStateException("生成防重复提交 Token 需要用户登录");
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        String redisKey = buildRedisKey(userId, token);

        redisTemplate.opsForValue().set(
                redisKey,
                TOKEN_VALUE,
                ttlMillis,
                TimeUnit.MILLISECONDS
        );

        log.debug("[ydsz-lock] [repeat-submit] 生成 Token | userId={}, token={}, ttl={}ms",
                userId, token, ttlMillis);

        return token;
    }

    /**
     * 获取防重复提交间隔窗口
     *
     * <p>同一用户对同一业务方法在 {@code intervalMillis} 窗口内只允许提交一次。
     * 基于 Redis {@code SET NX PX} 原子实现，配合 {@code @RepeatSubmit(interval=...)}
     * 在 Token 校验前拦截快速双击。用户未登录时跳过检查（无法绑定用户维度）。
     *
     * @param businessKey    业务方法标识（如 "类名#方法名"）
     * @param intervalMillis 间隔窗口（毫秒）
     * @return true-允许提交（窗口内首次），false-窗口内重复提交
     */
    public boolean acquireInterval(String businessKey, long intervalMillis) {
        if (!StringUtils.hasText(businessKey) || intervalMillis <= 0) {
            return true;
        }
        String userId = getCurrentUserId();
        if (!StringUtils.hasText(userId)) {
            return true;
        }
        String redisKey = INTERVAL_PREFIX + userId + ":" + businessKey;
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(
                    redisKey, TOKEN_VALUE, intervalMillis, TimeUnit.MILLISECONDS));
        } catch (Exception e) {
            log.warn("[ydsz-lock] [repeat-submit] 获取间隔窗口失败，放行 | key={} | error={}", redisKey, e.getMessage());
            return true;
        }
    }

    /**
     * 校验并消费 Token
     *
     * <p>校验 Token 是否有效（存在且未过期），校验成功后立即删除 Token。
     * Token 与当前用户 ID 绑定，防止 Token 被盗用。
     *
     * @param token 待校验的 Token
     * @return true=校验通过（Token 有效且已消费），false=校验失败
     */
    public boolean validateAndConsume(String token) {
        if (!StringUtils.hasText(token)) {
            log.warn("[ydsz-lock] [repeat-submit] Token 为空");
            return false;
        }

        String userId = getCurrentUserId();
        if (!StringUtils.hasText(userId)) {
            log.warn("[ydsz-lock] [repeat-submit] 用户未登录，无法校验 Token");
            return false;
        }

        String redisKey = buildRedisKey(userId, token);
        Boolean deleted = redisTemplate.delete(redisKey);

        if (Boolean.TRUE.equals(deleted)) {
            log.debug("[ydsz-lock] [repeat-submit] Token 校验通过并消费 | userId={}, token={}", userId, token);
            return true;
        } else {
            log.warn("[ydsz-lock] [repeat-submit] Token 无效或已过期 | userId={}, token={}", userId, token);
            return false;
        }
    }

    /**
     * 构建 Redis Key
     *
     * @param userId 用户 ID
     * @param token  Token 字符串
     * @return Redis Key
     */
    private String buildRedisKey(String userId, String token) {
        return TOKEN_PREFIX + userId + ":" + token;
    }

    /**
     * 获取当前登录用户 ID
     *
     * @return 用户 ID，未登录返回 null
     */
    private String getCurrentUserId() {
        LoginUser loginUser = AuthContextUtils.getCurrentOrNull();
        return loginUser != null ? loginUser.getUserId() : null;
    }
}
