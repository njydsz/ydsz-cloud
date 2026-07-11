/**
 * 降级与补偿层：处理监听器/消费者等关键路径落库失败时的兜底逻辑，保证数据最终一致性。
 *
 * <p>本包聚焦"零数据丢失"目标：当主流程（如 {@code OperationLogListener}）落库失败
 * 且重试仍失败时，启用本包中的 Fallback 记录器将事件持久化到独立日志文件
 * （{@code logs/audit-fallback.log}），由运维或对账任务在事后批量回灌。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@code OperationLogFallbackLogger} - 操作日志落库失败补偿器，使用独立 SLF4J logger
 *       （{@code audit-fallback}）以 JSON 行格式（JSONL）输出审计事件，便于 logstash/fluent-bit 采集</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>架构轻量</b>：不引入 MQ/死信队列/数据库中间表，保持 fallback 路径简单可靠</li>
 *   <li><b>独立 logger 命名</b>：避免污染主业务日志，便于通过 logback appender 路由到独立文件</li>
 *   <li><b>结构化输出</b>：采用 JSONL 格式，包含 fallbackAt/traceId/module/action/errorMessage
 *       等关键字段，方便后期解析与回灌</li>
 *   <li><b>静默兜底</b>：Fallback 记录器自身异常必须静默吞掉，绝不再次抛出影响主流程</li>
 *   <li><b>可观测</b>：通过 ERROR/WARN 级别日志配合告警，运维可主动感知补偿堆积</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>新增 fallback 类须遵循"独立 logger + JSONL 输出 + 静默异常"三原则</li>
 *   <li>logback 配置中须为对应 logger 配置 RollingFileAppender，保留周期建议 ≥ 30 天</li>
 *   <li>定期（如每天）通过 ETL 任务将 fallback 日志回灌到主表，回灌完成后清理文件</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.system.server.fallback;
