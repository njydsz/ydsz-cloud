package com.njydsz.pmis.agent.aop;

import com.alibaba.fastjson2.JSON;
import com.njydsz.pmis.agent.config.TokenQuotaProperties;
import com.njydsz.pmis.agent.dto.TokenUsage;
import com.njydsz.pmis.agent.engine.AgentContext;
import com.njydsz.pmis.agent.engine.llm.LlmProvider;
import com.njydsz.pmis.agent.engine.memory.TokenCounter;
import com.njydsz.pmis.agent.service.TokenQuotaService;
import com.njydsz.pmis.common.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * Token 配额 AOP 切面（P2-4 落地，P1-2 修复）。
 *
 * <p>拦截 {@link LlmProvider#chat} 与 {@link LlmProvider#chatForJson} 方法，在 LLM 调用前后
 * 自动统计 token 使用量：
 * <ol>
 *   <li>调用前：用 {@link TokenCounter#estimate} 估算 prompt token，检查配额</li>
 *   <li>调用后：用 {@link TokenCounter#estimate} 估算 completion token，记录使用量</li>
 * </ol>
 *
 * <p><b>P1-2 修复</b>：原切点仅拦截 {@code chat}，而 ReActLoop 调用的是
 * {@code chatForJson}（接口 default 方法）。由于 Spring AOP 基于代理，
 * {@code chatForJson} 内部对 {@code chat} 的自我调用不经过代理，导致 AOP 失效，
 * ReAct 循环的所有 LLM 调用都不计入 Token 配额。
 * 现切点同时拦截 {@code chat} 和 {@code chatForJson}，确保所有入口均被统计。
 * 由于 {@code chatForJson} 内部对 {@code chat} 的调用是 self-invocation（不经过代理），
 * 不会触发 {@code chat} 切点，因此不会重复统计。
 *
 * <p>配置开关：{@code pmis.agent.token-quota.enabled=false} 时切面降级为仅记录明细，
 * 不做配额限制（避免影响现有测试）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-4)
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class TokenQuotaAspect {

    private final TokenQuotaService tokenQuotaService;
    /** Token 配额配置（控制是否启用配额限制） */
    private final TokenQuotaProperties tokenQuotaProperties;

    /**
     * 拦截 LlmProvider.chat 与 chatForJson 方法，自动统计 token（P1-2 修复）。
     *
     * <p>方法签名：
     * <ul>
     *   <li>{@code String chat(String systemPrompt, String userPrompt, AgentContext context)}</li>
     *   <li>{@code <T> T chatForJson(String systemPrompt, String userPrompt, Class<T> type, AgentContext context)}</li>
     * </ul>
     *
     * <p>chatForJson 返回的是解析后的 Java 对象，统计 completion token 时先序列化为 JSON 再估算。
     *
     * @param pjp 连接点
     * @return LLM 返回的文本或解析后的对象
     * @throws Throwable 调用异常
     */
    @Around("execution(* com.njydsz.pmis.agent.engine.llm.LlmProvider.chat(..)) || "
            + "execution(* com.njydsz.pmis.agent.engine.llm.LlmProvider.chatForJson(..))")
    public Object aroundLlmCall(ProceedingJoinPoint pjp) throws Throwable {
        boolean isChatForJson = "chatForJson".equals(pjp.getSignature().getName());
        Object[] args = pjp.getArgs();

        String systemPrompt;
        String userPrompt;
        AgentContext context;
        if (isChatForJson) {
            // chatForJson(String, String, Class, AgentContext)
            systemPrompt = args.length > 0 ? (String) args[0] : null;
            userPrompt = args.length > 1 ? (String) args[1] : null;
            context = args.length > 3 ? (AgentContext) args[3] : null;
        } else {
            // chat(String, String, AgentContext)
            systemPrompt = args.length > 0 ? (String) args[0] : null;
            userPrompt = args.length > 1 ? (String) args[1] : null;
            context = args.length > 2 ? (AgentContext) args[2] : null;
        }

        String tenantId = resolveTenantId(context);
        String provider = resolveProviderName(pjp.getTarget());

        // 1. 调用前检查配额（enabled=false 时降级为仅记录明细，不做配额限制）
        int promptTokens = TokenCounter.estimate(systemPrompt) + TokenCounter.estimate(userPrompt);
        if (tokenQuotaProperties != null && tokenQuotaProperties.isEnabled()) {
            tokenQuotaService.checkQuota(tenantId, promptTokens);
        }

        // 2. 执行 LLM 调用
        long startMs = System.currentTimeMillis();
        Object result;
        try {
            result = pjp.proceed();
        } catch (Throwable e) {
            // 异常时仍记录尝试使用的 token（仅明细，不递增配额）
            recordUsageSafely(buildUsage(tenantId, context, provider, promptTokens, 0,
                    System.currentTimeMillis() - startMs));
            throw e;
        }
        long costMs = System.currentTimeMillis() - startMs;

        // 3. 调用后记录使用量
        String response = serializeResult(result, isChatForJson);
        int completionTokens = TokenCounter.estimate(response);
        recordUsageSafely(buildUsage(tenantId, context, provider, promptTokens,
                completionTokens, costMs));

        return result;
    }

    /**
     * 序列化 LLM 调用结果为字符串，用于估算 completion token。
     *
     * <p>chat 返回 String，直接 toString；chatForJson 返回解析后的对象，
     * 用 fastjson2 序列化为 JSON 再估算（更贴近实际 token 数）。
     *
     * @param result       LLM 调用结果
     * @param isChatForJson 是否为 chatForJson 调用
     * @return 字符串表示
     */
    private String serializeResult(Object result, boolean isChatForJson) {
        if (result == null) {
            return "";
        }
        if (isChatForJson) {
            try {
                return JSON.toJSONString(result);
            } catch (Exception e) {
                log.debug("[TokenQuotaAspect] 序列化 chatForJson 结果失败, 降级为 toString: {}", e.getMessage());
                return result.toString();
            }
        }
        return result.toString();
    }

    /** 安全记录使用量（异常不传播） */
    private void recordUsageSafely(TokenUsage usage) {
        try {
            tokenQuotaService.recordUsage(usage);
        } catch (Exception e) {
            log.warn("[TokenQuotaAspect] 记录使用量失败: tenant={} err={}",
                    usage.getTenantId(), e.getMessage());
        }
    }

    /** 构造 TokenUsage 对象 */
    private TokenUsage buildUsage(String tenantId, AgentContext ctx, String provider,
                                    int promptTokens, int completionTokens, long costMs) {
        return TokenUsage.builder()
                .tenantId(tenantId)
                .traceId(ctx == null ? null : ctx.getTraceId())
                .provider(provider)
                .bizRef(ctx == null ? null : ctx.getBizRef())
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .totalTokens(promptTokens + completionTokens)
                .costMs(costMs)
                .build();
    }

    /** 解析租户 ID：AgentContext 无 tenantId，统一从 TenantContext 获取 */
    private String resolveTenantId(AgentContext ctx) {
        return TenantContext.getTenantId();
    }

    /** 解析 LLM Provider 名称 */
    private String resolveProviderName(Object target) {
        if (target instanceof LlmProvider provider) {
            try {
                return provider.name();
            } catch (Exception e) {
                return "unknown";
            }
        }
        return target == null ? "unknown" : target.getClass().getSimpleName();
    }
}
