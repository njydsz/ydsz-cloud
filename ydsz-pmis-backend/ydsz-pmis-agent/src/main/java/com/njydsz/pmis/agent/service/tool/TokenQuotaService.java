package com.njydsz.pmis.agent.service;

import com.njydsz.pmis.agent.dto.tool.QuotaSummary;
import com.njydsz.pmis.agent.dto.tool.TokenUsage;

/**
 * 租户级 Token 配额服务（P2-4 落地）。
 *
 * <p>提供配额检查、使用量记录、配额查询能力，由 {@code TokenQuotaAspect}
 * 在 LLM 调用前后自动调用，业务代码无需感知。
 *
 * <p>使用方式（手动场景）：
 * <pre>
 * // 1. 调用前检查配额
 * tokenQuotaService.checkAndReserve(tenantId, estimatedPromptTokens);
 * try {
 *     String result = llmProvider.chat(sys, user, ctx);
 *     // 2. 调用后记录实际使用量
 *     TokenUsage usage = TokenUsage.builder()
 *             .tenantId(tenantId).promptTokens(promptTokens).completionTokens(completionTokens)
 *             .provider(providerName).build();
 *     tokenQuotaService.recordUsage(usage);
 * } catch (Exception e) {
 *     // 异常时释放预占（简单实现：不预占，仅事后记录）
 *     throw e;
 * }
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-4)
 */
public interface TokenQuotaService {

    /**
     * 检查租户是否有足够配额（不预占）。
     *
     * <p>配额不足时抛 {@code BizException(QUOTA_EXCEEDED)}。
     *
     * @param tenantId         租户 ID
     * @param estimatedTokens  预估 token 数
     */
    void checkQuota(String tenantId, long estimatedTokens);

    /**
     * 记录实际 token 使用量（原子递增已用配额 + 写入明细表）。
     *
     * <p>配额超限时仍然记录使用明细，但不递增配额（避免负数）。
     *
     * @param usage token 使用量
     */
    void recordUsage(TokenUsage usage);

    /**
     * 查询租户当月配额概览。
     *
     * @param tenantId 租户 ID
     * @return 配额概览（不存在时返回默认值）
     */
    QuotaSummary getQuotaSummary(String tenantId);

    /**
     * 重置租户当月配额（手动触发）。
     *
     * @param tenantId 租户 ID
     */
    void resetQuota(String tenantId);
}
