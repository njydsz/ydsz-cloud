package com.njydsz.cronjob.domain.enums;

import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.exception.registry.YdszExceptionCode;
import lombok.Getter;

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
 * @since 1.0.0
 */
@Getter
@YdszExceptionCode(module = "cronjob", description = "定时任务")
public enum CronjobExceptionCode implements ExceptionCode {

  // ==================== B92001-B92099 任务 ====================
  JOB_NOT_FOUND("B92001", "cronjob.job.not.found", 404),
  JOB_CODE_DUPLICATE("B92002", "cronjob.job.code.duplicate"),
  JOB_ALREADY_RUNNING("B92003", "cronjob.job.already.running"),
  JOB_HANDLER_NOT_FOUND("B92004", "cronjob.job.handler.not.found"),
  JOB_CRON_INVALID("B92005", "cronjob.job.cron.invalid"),

  // ==================== B92101-B92199 DAG ====================
  DAG_NOT_FOUND("B92101", "cronjob.dag.not.found", 404),
  DAG_CYCLE_DETECTED("B92102", "cronjob.dag.cycle.detected"),
  DAG_INSTANCE_NOT_FOUND("B92103", "cronjob.dag.instance.not.found", 404),
  DAG_NODE_NOT_FOUND("B92104", "cronjob.dag.node.not.found", 404),

  // ==================== B92201-B92299 任务历史/版本 ====================
  JOB_HISTORY_NOT_FOUND("B92201", "cronjob.job.history.not.found", 404),
  JOB_VERSION_NOT_FOUND("B92202", "cronjob.job.version.not.found", 404),
  JOB_LOG_NOT_FOUND("B92203", "cronjob.job.log.not.found", 404),

  // ==================== B92301-B92399 告警规则/Webhook ====================
  ALERT_RULE_NOT_FOUND("B92301", "cronjob.alert.rule.not.found", 404),
  WEBHOOK_NOT_FOUND("B92302", "cronjob.webhook.not.found", 404),
  CONNECTOR_NOT_FOUND("B92303", "cronjob.connector.not.found", 404);

  /** 错误码 */
  private final String code;

  /** 国际化消息键 */
  private final String key;

  /** HTTP 状态码 */
  private final int httpStatus;

  CronjobExceptionCode(String code, String key) {
    this(code, key, 400);
  }

  CronjobExceptionCode(String code, String key, int httpStatus) {
    this.code = code;
    this.key = key;
    this.httpStatus = httpStatus;
  }
}
