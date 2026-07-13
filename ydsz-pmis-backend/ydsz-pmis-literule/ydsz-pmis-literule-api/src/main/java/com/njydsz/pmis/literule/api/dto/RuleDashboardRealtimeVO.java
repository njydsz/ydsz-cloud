package com.njydsz.pmis.literule.api.dto;

import java.io.Serial;
import java.io.Serializable;

import lombok.Builder;
import lombok.Data;

/**
 * 规则引擎监控大盘 - 实时指标 VO
 *
 * <p>用于展示当前 QPS、活跃规则数等秒级实时指标。
 *
 * @author ydsz-pmis-team
 * @since 1.6.0
 */
@Data
@Builder
public class RuleDashboardRealtimeVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 当前注册规则数（引擎内存中） */
    private int registeredRules;

    /** 最近一次评估遍历的规则数 */
    private int lastEvaluatedRules;

    /** 最近 1 分钟评估次数 */
    private long recentEvaluations;

    /** 最近 1 分钟触发次数 */
    private long recentTriggered;

    /** 最近 1 分钟错误次数 */
    private long recentErrors;

    /** 当前 QPS（次/秒） */
    private double currentQps;

    /** 当前活跃规则数（最近 1 分钟有触发的规则） */
    private long activeRules;

    /** Trace 队列积压 */
    private int traceQueueSize;

    /** 服务器当前时间戳（毫秒） */
    private long timestamp;
}
