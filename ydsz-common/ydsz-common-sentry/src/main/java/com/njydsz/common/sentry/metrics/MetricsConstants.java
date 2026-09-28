package com.njydsz.common.sentry.metrics;

/**
 * 全平台统一 Sentry 指标名常量。
 *
 * <p>所有业务模块在进行 SentryObservation 埋点时，必须使用本类定义的常量，
 * 禁止 inline 字符串或 private static final 自定义常量。
 *
 * <p>命名规范：
 * <ul>
 *   <li>格式：{module}_{domain}_{action}_{unit}</li>
 *   <li>全小写 + 下划线分隔</li>
 *   <li>计数后缀：_total</li>
 *   <li>耗时后缀：_millis</li>
 *   <li>gauge 后缀：_count / _bytes / _ratio</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.28
 */
public final class MetricsConstants {

  private MetricsConstants() {
    throw new UnsupportedOperationException("Utility class");
  }

  // ==================== 通用基础设施 ====================
  /** HTTP 请求总数 */
  public static final String HTTP_REQUEST_TOTAL = "http.request.total";
  /** HTTP 请求耗时（毫秒） */
  public static final String HTTP_REQUEST_DURATION_MILLIS = "http.request.duration.millis";
  /** 数据库查询总数 */
  public static final String DB_QUERY_TOTAL = "db.query.total";
  /** 数据库查询耗时（毫秒） */
  public static final String DB_QUERY_DURATION_MILLIS = "db.query.duration.millis";
  /** 缓存命中总数 */
  public static final String CACHE_HIT_TOTAL = "cache.hit.total";
  /** 缓存未命中总数 */
  public static final String CACHE_MISS_TOTAL = "cache.miss.total";

  // ==================== Agent 引擎 ====================
  /** Agent 聊天总数 */
  public static final String AGENT_CHAT_TOTAL = "agent.chat.total";
  /** Agent 聊天耗时（毫秒） */
  public static final String AGENT_CHAT_DURATION_MILLIS = "agent.chat.duration.millis";
  /** Agent 聊天流总数 */
  public static final String AGENT_CHAT_STREAM_TOTAL = "agent.chat.stream.total";
  /** Agent 聊天流耗时（毫秒） */
  public static final String AGENT_CHAT_STREAM_DURATION_MILLIS = "agent.chat.stream.duration.millis";
  /** LLM 调用总数 */
  public static final String AGENT_LLM_CALLS_TOTAL = "agent.llm.calls.total";
  /** LLM 调用耗时（毫秒） */
  public static final String AGENT_LLM_CALLS_DURATION_MILLIS = "agent.llm.calls.duration.millis";

  // ==================== Cronjob 引擎 ====================
  /** 定时任务执行总数 */
  public static final String CRONJOB_EXECUTION_TOTAL = "cronjob.execution.total";
  /** Webhook 推送总数 */
  public static final String CRONJOB_WEBHOOK_SEND_TOTAL = "cronjob.webhook.send.total";
  /** Webhook 推送耗时（毫秒） */
  public static final String CRONJOB_WEBHOOK_SEND_DURATION_MILLIS = "cronjob.webhook.send.duration.millis";
  /** Webhook 重试总数 */
  public static final String CRONJOB_WEBHOOK_RETRY_TOTAL = "cronjob.webhook.retry.total";

  // ==================== Message 引擎 ====================
  /** 消息发送总数 */
  public static final String MESSAGE_SEND_TOTAL = "message.send.total";
  /** 消息发送耗时（毫秒） */
  public static final String MESSAGE_SEND_DURATION_MILLIS = "message.send.duration.millis";
  /** 消息投递失败总数 */
  public static final String MESSAGE_DELIVERY_FAIL_TOTAL = "message.delivery.fail.total";
  /** 死信队列重试总数 */
  public static final String MESSAGE_DLQ_RETRY_TOTAL = "message.dlq.retry.total";

  // ==================== NextWiki 引擎 ====================
  /** 文件分片上传总数 */
  public static final String NEXTWIKI_UPLOAD_CHUNK_TOTAL = "nextwiki.upload.chunk.total";
  /** 文件分片上传耗时（毫秒） */
  public static final String NEXTWIKI_UPLOAD_CHUNK_DURATION_MILLIS = "nextwiki.upload.chunk.duration.millis";
  /** 文件合并总数 */
  public static final String NEXTWIKI_UPLOAD_MERGE_TOTAL = "nextwiki.upload.merge.total";

  // ==================== Workflow 引擎 ====================
  /** 流程实例创建总数 */
  public static final String WORKFLOW_INSTANCE_CREATE_TOTAL = "workflow.instance.create.total";
  /** 流程任务完成总数 */
  public static final String WORKFLOW_TASK_COMPLETE_TOTAL = "workflow.task.complete.total";
  /** 流程耗时（毫秒） */
  public static final String WORKFLOW_INSTANCE_DURATION_MILLIS = "workflow.instance.duration.millis";

  // ==================== System 引擎 ====================
  /** 前端错误上报总数 */
  public static final String SYSTEM_FRONTEND_ERROR_TOTAL = "system.frontend.error.total";
  /** Web Vital 指标上报总数 */
  public static final String SYSTEM_WEB_VITAL_VALUE = "system.web.vital.value";

  // ==================== UserInfo 引擎 ====================
  /** 用户登录总数 */
  public static final String USERINFO_LOGIN_TOTAL = "userinfo.login.total";
  /** 用户登录耗时（毫秒） */
  public static final String USERINFO_LOGIN_DURATION_MILLIS = "userinfo.login.duration.millis";
  /** RBAC 鉴权总数 */
  public static final String USERINFO_RBAC_CHECK_TOTAL = "userinfo.rbac.check.total";
  /** RBAC 鉴权耗时（毫秒） */
  public static final String USERINFO_RBAC_CHECK_DURATION_MILLIS = "userinfo.rbac.check.duration.millis";

  // ==================== LiteRule 引擎 ====================
  /** 规则执行总数 */
  public static final String LITERULE_EXECUTE_TOTAL = "literule.execute.total";
  /** 规则执行耗时（毫秒） */
  public static final String LITERULE_EXECUTE_DURATION_MILLIS = "literule.execute.duration.millis";
}
