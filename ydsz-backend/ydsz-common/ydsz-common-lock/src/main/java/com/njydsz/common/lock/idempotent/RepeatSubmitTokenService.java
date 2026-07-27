package com.njydsz.common.lock.idempotent;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

import com.njydsz.common.auth.context.AuthContext;
import com.njydsz.common.security.LoginUser;

import lombok.extern.slf4j.Slf4j;

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
 * @see com.njydsz.common.lock.annotation.RepeatSubmit
 */
@Slf4j
public class RepeatSubmitTokenService {

    private static final String TOKEN_PREFIX = "ydsz:repeat:token:";
    private static final String TOKEN_VALUE = "1";

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

        log.debug("[RepeatSubmit] 生成 Token | userId={}, token={}, ttl={}ms",
                userId, token, ttlMillis);

        return token;
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
            log.warn("[RepeatSubmit] Token 为空");
            return false;
        }

        String userId = getCurrentUserId();
        if (!StringUtils.hasText(userId)) {
            log.warn("[RepeatSubmit] 用户未登录，无法校验 Token");
            return false;
        }

        String redisKey = buildRedisKey(userId, token);
        Boolean deleted = redisTemplate.delete(redisKey);

        if (Boolean.TRUE.equals(deleted)) {
            log.debug("[RepeatSubmit] Token 校验通过并消费 | userId={}, token={}", userId, token);
            return true;
        } else {
            log.warn("[RepeatSubmit] Token 无效或已过期 | userId={}, token={}", userId, token);
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
        LoginUser loginUser = AuthContext.getCurrentOrNull();
        return loginUser != null ? loginUser.getUserId() : null;
    }
}
