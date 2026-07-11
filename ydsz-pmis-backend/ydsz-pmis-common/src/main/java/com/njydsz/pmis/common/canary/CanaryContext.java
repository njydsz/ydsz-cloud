package com.njydsz.pmis.common.canary;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * 灰度路由上下文（P2-1 架构优化）。
 *
 * <p>携带灰度路由所需的上下文信息（用户 ID / 请求 ID / 自定义属性），
 * 由 {@link CanaryTarget} 实现方用于决策灰度版本。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Data
@Builder
public class CanaryContext implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户 ID */
    private String userId;

    /** 请求 ID / Trace ID */
    private String requestId;

    /** 租户 ID */
    private String tenantId;

    /** 灰度百分比（0-100） */
    private int canaryPercent;

    /** 自定义属性 */
    private Map<String, Object> attributes;

    /**
     * 快速构造方法
     *
     * @param userId 用户 ID
     * @return 灰度上下文
     */
    public static CanaryContext of(String userId) {
        return CanaryContext.builder().userId(userId).build();
    }

    /**
     * 快速构造方法（带灰度百分比）
     *
     * @param userId       用户 ID
     * @param canaryPercent 灰度百分比
     * @return 灰度上下文
     */
    public static CanaryContext of(String userId, int canaryPercent) {
        return CanaryContext.builder().userId(userId).canaryPercent(canaryPercent).build();
    }
}
