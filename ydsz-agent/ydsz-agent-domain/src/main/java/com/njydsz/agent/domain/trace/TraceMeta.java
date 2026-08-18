package com.njydsz.agent.domain.trace;

import java.time.LocalDateTime;

/**
 * 链路元数据。
 *
 * <p>记录执行链路的基本信息，用于调试面板展示和链路列表查询。
 *
 * @param traceId 链路 ID
 * @param conversationId 对话 ID
 * @param agentId Agent ID
 * @param startedAt 开始时间
 * @param status 执行状态
 * @param totalDurationMs 总耗时（毫秒）
 * @author ydsz-team
 * @since 2.18.0
 */
public record TraceMeta(
    String traceId,
    String conversationId,
    String agentId,
    LocalDateTime startedAt,
    String status,
    long totalDurationMs) {
}
