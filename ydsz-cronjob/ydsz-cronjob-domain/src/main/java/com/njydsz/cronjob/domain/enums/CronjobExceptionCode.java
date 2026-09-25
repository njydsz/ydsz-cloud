package com.njydsz.cronjob.domain.enums;

import lombok.Getter;

import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.exception.registry.YdszExceptionCode;

/**
 * 定时任务调度模块异常码枚举。
 *
 * <p>实现 {@link ExceptionCode} 接口，自动注册到 {@link com.njydsz.common.exception.code.ErrorCodeTable}， 支持
 * i18n 消息键、HTTP 状态码、异常分类。
 *
 * <p><b>编码区间</b>：
 *
 * <ul>
 *   <li>B92001-B92099 任务
 *   <li>B92101-B92199 DAG
 *   <li>B92201-B92299 任务历史/版本
 *   <li>B92301-B92399 告警规则/Webhook
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Getter
@YdszExceptionCode(module = "cronjob", description = "定时任务")
public enum CronjobExceptionCode implements ExceptionCode {

  // ==================== B92001-B92099 任务 ====================
  /** Job not found */
  JOB_NOT_FOUND("B92001", "cronjob.job.not.found", 404),
  /** Job code duplicate */
  JOB_CODE_DUPLICATE("B92002", "cronjob.job.code.duplicate"),
  /** Job already running */
  JOB_ALREADY_RUNNING("B92003", "cronjob.job.already.running"),
  /** Job handler not found */
  JOB_HANDLER_NOT_FOUND("B92004", "cronjob.job.handler.not.found"),
  /** Job cron invalid */
  JOB_CRON_INVALID("B92005", "cronjob.job.cron.invalid"),

  // ==================== B92101-B92199 DAG ====================
  /** Dag not found */
  DAG_NOT_FOUND("B92101", "cronjob.dag.not.found", 404),
  /** Dag cycle detected */
  DAG_CYCLE_DETECTED("B92102", "cronjob.dag.cycle.detected"),
  /** Dag instance not found */
  DAG_INSTANCE_NOT_FOUND("B92103", "cronjob.dag.instance.not.found", 404),
  /** Dag node not found */
  DAG_NODE_NOT_FOUND("B92104", "cronjob.dag.node.not.found", 404),

  // ==================== B92201-B92299 任务历史/版本 ====================
  /** Job history not found */
  JOB_HISTORY_NOT_FOUND("B92201", "cronjob.job.history.not.found", 404),
  /** Job version not found */
  JOB_VERSION_NOT_FOUND("B92202", "cronjob.job.version.not.found", 404),
  /** Job log not found */
  JOB_LOG_NOT_FOUND("B92203", "cronjob.job.LOG.not.found", 404),

  // ==================== B92301-B92399 告警规则/Webhook ====================
  /** Alert rule not found */
  ALERT_RULE_NOT_FOUND("B92301", "cronjob.alert.rule.not.found", 404),
  /** Webhook not found */
  WEBHOOK_NOT_FOUND("B92302", "cronjob.webhook.not.found", 404),
  /** Webhook send failed */
  WEBHOOK_SEND_FAILED("B92304", "cronjob.webhook.send.failed", 502),
  /** Connector not found */
  CONNECTOR_NOT_FOUND("B92303", "cronjob.connector.not.found", 404),

  // ==================== B92006-B92099 参数校验 ====================
  /** Generic parameter error */
  PARAM_ERROR("B92006", "cronjob.param.required"),
  /** Invalid job configuration */
  CONFIG_INVALID("B92007", "cronjob.config.invalid"),
  /** Thread pool name is required */
  THREAD_POOL_NAME_REQUIRED("B92008", "cronjob.thread.pool.name.required"),
  /** Thread pool instance cannot be null */
  THREAD_POOL_INSTANCE_REQUIRED("B92009", "cronjob.thread.pool.instance.required"),

  // ==================== B92105-B92199 DAG 校验 ====================
  /** DAG edge disallows self-loop */
  DAG_EDGE_SELF_LOOP("B92105", "cronjob.dag.edge.self.loop"),

  // ==================== B92401-B92499 GLUE ====================
  /** GLUE job execution context missing jobId */
  GLUE_CONTEXT_MISSING_JOBID("B92401", "cronjob.glue.context.missing.jobId"),
  /** GlueCodeService unregistered */
  GLUE_SERVICE_UNREGISTERED("B92402", "cronjob.glue.service.unregistered"),
  /** GLUE code is empty */
  GLUE_CODE_EMPTY("B92403", "cronjob.glue.code.empty"),
  /** Unsupported GLUE language type */
  GLUE_LANGUAGE_UNSUPPORTED("B92404", "cronjob.glue.language.unsupported"),
  /** GLUE code instantiation failed */
  GLUE_INSTANTIATION_FAILED("B92405", "cronjob.glue.instantiation.failed"),
  /** GroovyDockerSandboxExecutor unregistered */
  GLUE_SANDBOX_UNREGISTERED("B92406", "cronjob.glue.sandbox.unregistered"),
  /** Groovy Docker sandbox execution failed */
  GLUE_SANDBOX_FAILED("B92407", "cronjob.glue.sandbox.failed"),
  /** SandboxScriptExecutor unregistered (Python GLUE) */
  GLUE_PYTHON_EXECUTOR_UNREGISTERED("B92408", "cronjob.glue.python.executor.unregistered");

  /** 错误码 */
  private final String code;

  /** 国际化消息键 */
  private final String key;

  /** 默认 HTTP 状态码：参数错误 */
  private static final int DEFAULT_HTTP_STATUS = 400;

  /** HTTP 状态码 */
  private final int httpStatus;

  CronjobExceptionCode(String code, String key) {
    this(code, key, DEFAULT_HTTP_STATUS);
  }

  CronjobExceptionCode(String code, String key, int httpStatus) {
    this.code = code;
    this.key = key;
    this.httpStatus = httpStatus;
  }
}
