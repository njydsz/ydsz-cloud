package com.njydsz.pmis.agent.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 租户 Token 配额概览（P2-4 落地）。
 *
 * <p>供前端展示配额使用情况，包含已用/总量/剩余/百分比/状态。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-4)
 */
@Data
@Builder
public class QuotaSummary {

    /** 租户 ID */
    private String tenantId;

    /** 配额月份 YYYYMM */
    private String quotaMonth;

    /** 月度配额上限 */
    private long totalQuota;

    /** 已使用 token 数 */
    private long usedTokens;

    /** 剩余 token 数 */
    private long remainingTokens;

    /** 使用百分比（0-100，保留 2 位小数） */
    private BigDecimal usagePercentage;

    /** 配额状态：ACTIVE/RUNOUT/RESET */
    private String status;

    /** 上次重置时间 */
    private LocalDateTime resetAt;

    /**
     * 根据总量和已用量构造概览。
     *
     * @param tenantId    租户 ID
     * @param quotaMonth  月份
     * @param totalQuota  总量
     * @param usedTokens  已用
     * @param status      状态
     * @param resetAt     重置时间
     * @return 概览对象
     */
    public static QuotaSummary of(String tenantId, String quotaMonth,
                                    long totalQuota, long usedTokens,
                                    String status, LocalDateTime resetAt) {
        long remaining = Math.max(0, totalQuota - usedTokens);
        BigDecimal percentage = totalQuota > 0
                ? BigDecimal.valueOf(usedTokens)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(totalQuota), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        return QuotaSummary.builder()
                .tenantId(tenantId)
                .quotaMonth(quotaMonth)
                .totalQuota(totalQuota)
                .usedTokens(usedTokens)
                .remainingTokens(remaining)
                .usagePercentage(percentage)
                .status(status)
                .resetAt(resetAt)
                .build();
    }
}
