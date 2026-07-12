package com.njydsz.pmis.common.aspect;

import com.njydsz.pmis.common.annotation.RequireReAuth;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.util.SnowflakeIdGenerator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.Objects;

/**
 * 敏感操作二次认证 AOP
 *
 * <p>校验流程：
 * <ol>
 *   <li>从请求头 {@code X-Re-Auth-Token} 读取 token</li>
 *   <li>Redis 校验 token 是否存在 + 未过期</li>
 *   <li>通过则继续并删除 token（一次性使用）；失败则抛出异常</li>
 * </ol>
 *
 * <p>token 颁发由 {@link #issueToken} 方法完成，业务服务注入此 Aspect 调用。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RequireReAuthAspect {

    /** 二次认证请求头名称 */
    private static final String HEADER = "X-Re-Auth-Token";
    /** 用户 ID 请求头 */
    private static final String USER_ID_HEADER = "X-User-Id";
    /** Redis key 前缀 */
    private static final String KEY_PREFIX = "pmis:reauth:";

    /** Redis 操作模板 */
    private final StringRedisTemplate redisTemplate;

    /**
     * 环绕增强：校验二次认证 token，通过后执行目标方法
     *
     * @param pjp 连接点
     * @param ann 二次认证注解
     * @return 目标方法返回值
     * @throws Throwable 目标方法抛出的异常
     */
    @Around("@annotation(ann)")
    public Object around(ProceedingJoinPoint pjp, RequireReAuth ann) throws Throwable {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            throw new BizException("非 Web 请求上下文，不支持二次认证");
        }
        HttpServletRequest request = attrs.getRequest();
        String token = request.getHeader(HEADER);
        if (!StringUtils.hasText(token)) {
            throw new BizException("敏感操作需要二次认证: " + ann.name());
        }
        String userId = request.getHeader(USER_ID_HEADER);
        if (!StringUtils.hasText(userId)) {
            userId = "anonymous";
        }
        String redisKey = KEY_PREFIX + ann.code() + ":" + userId + ":" + token;
        String stored = redisTemplate.opsForValue().get(redisKey);
        if (!StringUtils.hasText(stored)) {
            log.warn("[ReAuth] 二次认证失败: code={}, user={}", ann.code(), userId);
            throw new BizException("二次认证 token 无效或已过期");
        }
        // 一次性 token，使用后立即失效
        redisTemplate.delete(redisKey);

        return pjp.proceed();
    }

    /**
     * 颁发二次认证 token，写入 Redis 并设置过期时间。
     *
     * <p>业务服务（如 ReAuthService）注入此 Aspect 调用此方法获取 token，
     * 前端拿到 token 后在后续敏感操作请求头中携带 {@code X-Re-Auth-Token}。
     *
     * @param operationCode 操作码
     * @param userId        用户 ID
     * @param ttlSeconds    有效期（秒）
     * @return 二次认证 token
     */
    public String issueToken(String operationCode, String userId, int ttlSeconds) {
        String token = SnowflakeIdGenerator.nextIdStr();
        String key = KEY_PREFIX + operationCode + ":" + userId + ":" + token;
        redisTemplate.opsForValue().set(key, "1", Duration.ofSeconds(ttlSeconds));
        return token;
    }
}
