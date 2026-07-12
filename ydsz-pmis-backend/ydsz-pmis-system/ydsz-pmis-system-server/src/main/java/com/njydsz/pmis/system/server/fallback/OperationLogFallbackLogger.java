package com.njydsz.pmis.system.server.fallback;

import com.alibaba.fastjson2.JSON;
import com.njydsz.pmis.common.audit.event.OperationLogEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 操作日志补偿记录器
 *
 * <p>当 {@link com.njydsz.pmis.system.server.listener.OperationLogListener} 落库失败且重试仍失败时，
 * 将事件 JSON 写入独立的 "audit-fallback" logger，由 logback 配置滚动文件 appender
 * 输出到 {@code logs/audit-fallback.log}，便于运维或对账任务后期补录。</p>
 *
 * <p>设计原则：
 * <ul>
 *   <li>不引入 MQ/死信队列，保持架构简单</li>
 *   <li>使用独立的 SLF4J logger，避免污染主业务日志</li>
 *   <li>JSON 行格式（JSONL），便于 logstash/fluent-bit 采集后批量回灌</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Component
public class OperationLogFallbackLogger {

    /** 独立 logger 名称，logback 中需配置对应 appender */
    private static final Logger FALLBACK_LOGGER = LoggerFactory.getLogger("audit-fallback");

    /**
     * 记录落库失败的审计事件。
     *
     * @param event 操作日志事件
     * @param error 落库时抛出的异常
     */
    public void log(OperationLogEvent event, Throwable error) {
        try {
            FallbackRecord record = new FallbackRecord(
                    System.currentTimeMillis(),
                    event.getTraceId(),
                    event.getModule(),
                    event.getAction(),
                    event.getBizType(),
                    event.getBizId(),
                    event.getUserId(),
                    event.getUsername(),
                    event.getStatus(),
                    error == null ? "unknown" : error.getMessage()
            );
            FALLBACK_LOGGER.info(JSON.toJSONString(record));
        } catch (Exception ignored) {
            // 补偿记录本身失败，不应再抛出异常
        }
    }

    /**
     * 补偿记录结构（JSON 行格式）
     */
    private record FallbackRecord(
            long fallbackAt,
            String traceId,
            String module,
            String action,
            String bizType,
            String bizId,
            String userId,
            String username,
            String status,
            String errorMessage
    ) {
    }
}
