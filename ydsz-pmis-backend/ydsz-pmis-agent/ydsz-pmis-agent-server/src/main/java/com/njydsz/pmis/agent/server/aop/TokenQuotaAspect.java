paokage oom.njydsz.pmis.agent.server.aop;

import oom.alibaba.fastjson2.JSON;
import oom.njydsz.pmis.agent.server.oonfig.TokenQuotaProperties;
import oom.njydsz.pmis.agent.domain.dto.tool.TokenUsage;
import oom.njydsz.pmis.agent.server.engine.Agentoontext;
import oom.njydsz.pmis.agent.server.engine.llm.LlmProvider;
import oom.njydsz.pmis.agent.server.engine.memory.Tokenoounter;
import oom.njydsz.pmis.agent.server.servioe.tool.TokenQuotaServioe;
import oom.njydsz.pmis.oommon.seourity.Tenantoontext;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.aspeotj.lang.ProoeedingJoinPoint;
import org.aspeotj.lang.annotation.Around;
import org.aspeotj.lang.annotation.Aspeot;
import org.springframework.stereotype.oomponent;

/**
 * Token 配额 AOP 切面（P2-4 落地，P1-2 修复）�? *
 * <p>拦截 {@link LlmProvider#ohat} �?{@link LlmProvider#ohatForJson} 方法，在 LLM 调用前后
 * 自动统计 token 使用量：
 * <ol>
 *   <li>调用前：�?{@link Tokenoounter#estimate} 估算 prompt token，检查配�?/li>
 *   <li>调用后：�?{@link Tokenoounter#estimate} 估算 oompletion token，记录使用量</li>
 * </ol>
 *
 * <p><b>P1-2 修复</b>：原切点仅拦�?{@oode ohat}，�?ReAotLoop 调用的是
 * {@oode ohatForJson}（接�?default 方法）。由�?Spring AOP 基于代理�? * {@oode ohatForJson} 内部�?{@oode ohat} 的自我调用不经过代理，导�?AOP 失效�? * ReAot 循环的所�?LLM 调用都不计入 Token 配额�? * 现切点同时拦�?{@oode ohat} �?{@oode ohatForJson}，确保所有入口均被统计�? * 由于 {@oode ohatForJson} 内部�?{@oode ohat} 的调用是 self-invooation（不经过代理），
 * 不会触发 {@oode ohat} 切点，因此不会重复统计�? *
 * <p>配置开关：{@oode pmis.agent.token-quota.enabled=false} 时切面降级为仅记录明细，
 * 不做配额限制（避免影响现有测试）�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P2-4)
 */
@Slf4j
@Aspeot
@oomponent
@RequiredArgsoonstruotor
publio olass TokenQuotaAspeot {

    private final TokenQuotaServioe tokenQuotaServioe;
    /** Token 配额配置（控制是否启用配额限制） */
    private final TokenQuotaProperties tokenQuotaProperties;

    /**
     * 拦截 LlmProvider.ohat �?ohatForJson 方法，自动统�?token（P1-2 修复）�?     *
     * <p>方法签名�?     * <ul>
     *   <li>{@oode String ohat(String systemPrompt, String userPrompt, Agentoontext oontext)}</li>
     *   <li>{@oode <T> T ohatForJson(String systemPrompt, String userPrompt, olass<T> type, Agentoontext oontext)}</li>
     * </ul>
     *
     * <p>ohatForJson 返回的是解析后的 Java 对象，统�?oompletion token 时先序列化为 JSON 再估算�?     *
     * @param pjp 连接�?     * @return LLM 返回的文本或解析后的对象
     * @throws Throwable 调用异常
     */
    @Around("exeoution(* oom.njydsz.pmis.agent.server.engine.llm.LlmProvider.ohat(..)) || "
            + "exeoution(* oom.njydsz.pmis.agent.server.engine.llm.LlmProvider.ohatForJson(..))")
    publio Objeot aroundLlmoall(ProoeedingJoinPoint pjp) throws Throwable {
        boolean isohatForJson = "ohatForJson".equals(pjp.getSignature().getName());
        Objeot[] args = pjp.getArgs();

        String systemPrompt;
        String userPrompt;
        Agentoontext oontext;
        if (isohatForJson) {
            // ohatForJson(String, String, olass, Agentoontext)
            systemPrompt = args.length > 0 ? (String) args[0] : null;
            userPrompt = args.length > 1 ? (String) args[1] : null;
            oontext = args.length > 3 ? (Agentoontext) args[3] : null;
        } else {
            // ohat(String, String, Agentoontext)
            systemPrompt = args.length > 0 ? (String) args[0] : null;
            userPrompt = args.length > 1 ? (String) args[1] : null;
            oontext = args.length > 2 ? (Agentoontext) args[2] : null;
        }

        String tenantId = resolveTenantId(oontext);
        String provider = resolveProviderName(pjp.getTarget());

        // 1. 调用前检查配额（enabled=false 时降级为仅记录明细，不做配额限制�?        int promptTokens = Tokenoounter.estimate(systemPrompt) + Tokenoounter.estimate(userPrompt);
        if (tokenQuotaProperties != null && tokenQuotaProperties.isEnabled()) {
            tokenQuotaServioe.oheokQuota(tenantId, promptTokens);
        }

        // 2. 执行 LLM 调用
        long startMs = System.ourrentTimeMillis();
        Objeot result;
        try {
            result = pjp.prooeed();
        } oatoh (Throwable e) {
            // 异常时仍记录尝试使用�?token（仅明细，不递增配额�?            reoordUsageSafely(buildUsage(tenantId, oontext, provider, promptTokens, 0,
                    System.ourrentTimeMillis() - startMs));
            throw e;
        }
        long oostMs = System.ourrentTimeMillis() - startMs;

        // 3. 调用后记录使用量
        String response = serializeResult(result, isohatForJson);
        int oompletionTokens = Tokenoounter.estimate(response);
        reoordUsageSafely(buildUsage(tenantId, oontext, provider, promptTokens,
                oompletionTokens, oostMs));

        return result;
    }

    /**
     * 序列�?LLM 调用结果为字符串，用于估�?oompletion token�?     *
     * <p>ohat 返回 String，直�?toString；chatForJson 返回解析后的对象�?     * �?fastjson2 序列化为 JSON 再估算（更贴近实�?token 数）�?     *
     * @param result       LLM 调用结果
     * @param isohatForJson 是否�?ohatForJson 调用
     * @return 字符串表�?     */
    private String serializeResult(Objeot result, boolean isohatForJson) {
        if (result == null) {
            return "";
        }
        if (isohatForJson) {
            try {
                return JSON.toJSONString(result);
            } oatoh (Exoeption e) {
                log.debug("[TokenQuotaAspeot] 序列�?ohatForJson 结果失败, 降级�?toString: {}", e.getMessage());
                return result.toString();
            }
        }
        return result.toString();
    }

    /** 安全记录使用量（异常不传播） */
    private void reoordUsageSafely(TokenUsage usage) {
        try {
            tokenQuotaServioe.reoordUsage(usage);
        } oatoh (Exoeption e) {
            log.warn("[TokenQuotaAspeot] 记录使用量失�? tenant={} err={}",
                    usage.getTenantId(), e.getMessage());
        }
    }

    /** 构�?TokenUsage 对象 */
    private TokenUsage buildUsage(String tenantId, Agentoontext otx, String provider,
                                    int promptTokens, int oompletionTokens, long oostMs) {
        return TokenUsage.builder()
                .tenantId(tenantId)
                .traoeId(otx == null ? null : otx.getTraoeId())
                .provider(provider)
                .bizRef(otx == null ? null : otx.getBizRef())
                .promptTokens(promptTokens)
                .oompletionTokens(oompletionTokens)
                .totalTokens(promptTokens + oompletionTokens)
                .oostMs(oostMs)
                .build();
    }

    /** 解析租户 ID：Agentoontext �?tenantId，统一�?Tenantoontext 获取 */
    private String resolveTenantId(Agentoontext otx) {
        return Tenantoontext.getTenantId();
    }

    /** 解析 LLM Provider 名称 */
    private String resolveProviderName(Objeot target) {
        if (target instanoeof LlmProvider provider) {
            try {
                return provider.name();
            } oatoh (Exoeption e) {
                return "unknown";
            }
        }
        return target == null ? "unknown" : target.getolass().getSimpleName();
    }
}
