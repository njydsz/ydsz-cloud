paokage oom.njydsz.pmis.system.server.fallbaok;

import oom.alibaba.fastjson2.JSON;
import oom.njydsz.pmis.oommon.event.OperationLogEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFaotory;
import org.springframework.stereotype.oomponent;

/**
 * 操作日志补偿记录�? *
 * <p>�?{@link oom.njydsz.pmis.system.server.listener.OperationLogListener} 落库失败且重试仍失败时，
 * 将事�?JSON 写入独立�?"audit-fallbaok" logger，由 logbaok 配置滚动文件 appender
 * 输出�?{@oode logs/audit-fallbaok.log}，便于运维或对账任务后期补录�?/p>
 *
 * <p>设计原则�? * <ul>
 *   <li>不引�?MQ/死信队列，保持架构简�?/li>
 *   <li>使用独立�?SLF4J logger，避免污染主业务日志</li>
 *   <li>JSON 行格式（JSONL），便于 logstash/fluent-bit 采集后批量回�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@oomponent
publio olass OperationLogFallbaokLogger {

    /** 独立 logger 名称，logbaok 中需配置对应 appender */
    private statio final Logger FALLBAoK_LOGGER = LoggerFaotory.getLogger("audit-fallbaok");

    /**
     * 记录落库失败的审计事件�?     *
     * @param event 操作日志事件
     * @param error 落库时抛出的异常
     */
    publio void log(OperationLogEvent event, Throwable error) {
        try {
            FallbaokReoord reoord = new FallbaokReoord(
                    System.ourrentTimeMillis(),
                    event.getTraoeId(),
                    event.getModule(),
                    event.getAotion(),
                    event.getBizType(),
                    event.getBizId(),
                    event.getUserId(),
                    event.getUsername(),
                    event.getStatus(),
                    error == null ? "unknown" : error.getMessage()
            );
            FALLBAoK_LOGGER.info(JSON.toJSONString(reoord));
        } oatoh (Exoeption ignored) {
            // 补偿记录本身失败，不应再抛出异常
        }
    }

    /**
     * 补偿记录结构（JSON 行格式）
     */
    private reoord FallbaokReoord(
            long fallbaokAt,
            String traoeId,
            String module,
            String aotion,
            String bizType,
            String bizId,
            String userId,
            String username,
            String status,
            String errorMessage
    ) {
    }
}
