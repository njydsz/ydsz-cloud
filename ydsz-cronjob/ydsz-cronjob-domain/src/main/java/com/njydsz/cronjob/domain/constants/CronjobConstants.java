package com.njydsz.cronjob.domain.constants;

/**
 * 定时任务模块常量类。
 *
 * <p>集中管理 cronjob 模块中各层使用的状态码、HTTP 方法等魔法值，
 * 避免散落在 Controller / Repository / Service 中造成维护负担。
 *
 * <p>已有枚举的状态（如子任务状态 PENDING/RUNNING/SUCCESS/FAILED）
 * 请直接使用 {@link com.njydsz.cronjob.domain.enums.JobTaskStatusEnum}，
 * 不在本类重复定义。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class CronjobConstants {

  private CronjobConstants() {
    throw new UnsupportedOperationException("常量类不可实例化");
  }

  // ============================== 任务状态 ==============================

  /** 任务状态：正常运行。 */
  public static final String JOB_STATUS_NORMAL = "NORMAL";

  /** 任务状态：已暂停（手动暂停，可通过 resume 恢复）。 */
  public static final String JOB_STATUS_PAUSED = "PAUSED";

  /** 任务状态：自动暂停（连续失败熔断触发，可通过 autoResume 恢复）。 */
  public static final String JOB_STATUS_AUTO_PAUSED = "AUTO_PAUSED";

  /** 任务状态：已停止（手动停止，不可通过 resume 恢复，需重新启用）。 */
  public static final String JOB_STATUS_STOPPED = "STOPPED";

  /** 任务状态：错误（执行异常，需人工介入）。 */
  public static final String JOB_STATUS_ERROR = "ERROR";

  /** 任务状态：已删除（软删除终态）。 */
  public static final String JOB_STATUS_DELETED = "DELETED";

  // ============================== WebHook 状态 ==============================

  /** WebHook 状态：激活（事件推送中）。 */
  public static final String WEBHOOK_STATUS_ACTIVE = "ACTIVE";

  /** WebHook 状态：未激活（暂停事件推送）。 */
  public static final String WEBHOOK_STATUS_INACTIVE = "INACTIVE";

  // ============================== HTTP 方法 ==============================

  /** HTTP 方法：POST。 */
  public static final String HTTP_METHOD_POST = "POST";

  /** HTTP 方法：PUT。 */
  public static final String HTTP_METHOD_PUT = "PUT";
}
