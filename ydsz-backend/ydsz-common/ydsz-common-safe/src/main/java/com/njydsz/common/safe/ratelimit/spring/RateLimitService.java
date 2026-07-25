package com.njydsz.common.safe.ratelimit.spring;

import com.njydsz.common.safe.ratelimit.annotation.SentinelRateLimit;
import com.njydsz.common.safe.ratelimit.core.RateLimitManager;
import com.njydsz.common.safe.ratelimit.model.RateLimitContext;
import com.njydsz.common.safe.ratelimit.model.RateLimitDecision;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Spring 限流服务门面
 *
 * <p>提供编程式调用限流的入口，业务代码可以直接注入使用，无需依赖 AOP 注解。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * @Autowired
 * private RateLimitService rateLimitService;
 *
 * public void businessMethod() {
 *     RateLimitDecision decision = rateLimitService.check("user.login");
 *     if (decision.isBlocked()) {
 *         throw new BusinessException("RATE_LIMITED");
 *     }
 *     // 执行业务
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class RateLimitService {

    private final RateLimitManager rateLimitManager;

    /**
     * 检查限流（不带上下文）
     */
    public RateLimitDecision check(String resource) {
        return check(RateLimitContext.builder().resource(resource).build());
    }

    /**
     * 检查限流（带上下文）
     */
    public RateLimitDecision check(RateLimitContext context) {
        return rateLimitManager.decide(context);
    }

    /**
     * 检查限流（带注解 + 参数）
     */
    public RateLimitDecision check(SentinelRateLimit annotation, Object... args) {
        StringBuilder keyBuilder = new StringBuilder(annotation.resource());
        if (annotation.keyParam() >= 0 && annotation.keyParam() < args.length
                && args[annotation.keyParam()] != null) {
            keyBuilder.append(":").append(args[annotation.keyParam()]);
        }
        RateLimitContext context = RateLimitContext.builder()
                .resource(keyBuilder.toString())
                .args(args)
                .build();
        return check(context);
    }

    /**
     * 快捷方法：判断是否通过
     */
    public boolean isPass(String resource) {
        return check(resource).isPass();
    }
}
