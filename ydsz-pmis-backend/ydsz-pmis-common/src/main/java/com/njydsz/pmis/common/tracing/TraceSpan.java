package com.njydsz.pmis.common.tracing;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 统一追踪 Span 数据模型（P2-1 架构优化）。
 *
 * <p>替代 agent / message / cronjob / literule 各模块各自定义的 TraceContext。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Data
@Builder
public class TraceSpan implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Span ID（唯一标识） */
    private String spanId;

    /** Trace ID（同一请求链路共享） */
    private String traceId;

    /** 父 Span ID */
    private String parentSpanId;

    /** 模块名（agent / message / cronjob / literule） */
    private String module;

    /** 操作名称 */
    private String operationName;

    /** 开始时间 */
    private LocalDateTime startedAt;

    /** 结束时间 */
    private LocalDateTime finishedAt;

    /** 耗时（毫秒） */
    private long elapsedMs;

    /** 状态: SUCCESS / FAILED / RUNNING */
    private String status;

    /** 错误信息 */
    private String errorMessage;

    /** 租户 ID */
    private String tenantId;

    /** 业务类型 */
    private String bizType;

    /** 业务 ID */
    private String bizId;

    /** 标签（键值对） */
    private Map<String, String> tags;

    /** 事件日志 */
    private String events;
}
