package com.njydsz.common.exception.code;

import lombok.Getter;

import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.exception.registry.YdszExceptionCode;

/**
 * 核心模块异常码。
 *
 * <p>业务通用码（A01/A04/A06/B01/B02 系列）+ 通用成功/失败码。 覆盖业务逻辑校验、系统内部错误、外部服务调用、文件操作等通用场景。
 *
 * <p>限流相关码已迁移至 {@link RateLimitExceptionCode}， 认证/权限/安全码已迁移至 {@link SecurityExceptionCode}。
 *
 * @author ydsz-team
 * @since 2.0.0
 * @see SecurityExceptionCode
 * @see RateLimitExceptionCode
 */
@Getter
@YdszExceptionCode(module = "core", description = "核心模块业务异常码")
public enum CoreExceptionCode implements ExceptionCode {

  // ==================== 成功 ====================

  /**
   * 操作成功（原 ResponseCode.SUCCESS 000000）
   *
   * @return 处理结果
   */
  SUCCESS("A00000", "success", 200, false, 0),

  // ==================== A01 参数/业务异常 ====================

  /**
   * 操作失败（原 ResponseCode.FAIL 111111）
   *
   * @return 处理结果
   */
  FAIL("A01051", "operation.fail", 400, false, 0),
  /**
   * 参数错误（原 ResponseCode.PARAM_ERROR 100001）
   *
   * @return 处理结果
   */
  PARAM_ERROR("A01052", "param.error", 400, false, 0),
  /**
   * 非法参数
   *
   * @return 处理结果
   */
  ILLEGAL_ARGUMENT("A01053", "illegal.argument", 400, false, 0),
  /**
   * 请求格式无效
   *
   * @return 处理结果
   */
  INVALID_REQUEST_FORMAT("A01054", "invalid.request.format", 400, false, 0),
  /**
   * 业务状态无效
   *
   * @return 处理结果
   */
  INVALID_BUSINESS_STATE("A01055", "invalid.business.state", 400, false, 0),
  /**
   * 业务规则违反
   *
   * @return 处理结果
   */
  BUSINESS_RULE_VIOLATION("A01056", "business.rule.violation", 400, false, 0),
  /**
   * 通用业务错误
   *
   * @return 处理结果
   */
  BUSINESS_ERROR("A01057", "business.error", 400, false, 0),
  /**
   * 请求方法不允许（原 ResponseCode.METHOD_NOT_ALLOWED 100405）
   *
   * @return 处理结果
   */
  METHOD_NOT_ALLOWED("A01058", "method.not.allowed", 405, false, 0),
  /**
   * 重复提交
   *
   * @return 处理结果
   */
  DUPLICATE_SUBMISSION("A01059", "duplicate.submission", 400, false, 0),
  /**
   * 流程状态无效
   *
   * @return 处理结果
   */
  INVALID_FLOW_STATE("A01060", "invalid.flow.state", 400, false, 0),
  /**
   * 乐观锁冲突/并发冲突（可恢复：刷新数据后重试）
   *
   * @return 处理结果
   */
  OPTIMISTIC_LOCK_CONFLICT("A01061", "optimistic.lock.conflict", 409, true, 0),
  /**
   * 唯一约束冲突
   *
   * @return 处理结果
   */
  UNIQUE_CONSTRAINT_VIOLATION("A01062", "unique.constraint.violation", 409, false, 0),
  /**
   * 外键约束违反
   *
   * @return 处理结果
   */
  FOREIGN_KEY_VIOLATION("A01063", "foreign.key.violation", 409, false, 0),
  /**
   * 非空约束违反
   *
   * @return 处理结果
   */
  NOT_NULL_VIOLATION("A01064", "not.null.violation", 409, false, 0),
  /**
   * 检查约束违反
   *
   * @return 处理结果
   */
  CHECK_CONSTRAINT_VIOLATION("A01065", "check.constraint.violation", 409, false, 0),

  // ==================== A04 数据/资源异常 ====================

  /**
   * 资源不存在（原 ResponseCode.NOT_FOUND 100404）
   *
   * @return 处理结果
   */
  NOT_FOUND("A04051", "not.found", 404, false, 0),
  /**
   * 资源冲突（原 ResponseCode.CONFLICT 100409）
   *
   * @return 处理结果
   */
  CONFLICT("A04052", "conflict", 409, false, 0),
  /**
   * 数据未找到
   *
   * @return 处理结果
   */
  DATA_NOT_FOUND("A04053", "data.not.found", 404, false, 0),
  /**
   * 资源未找到
   *
   * @return 处理结果
   */
  RESOURCE_NOT_FOUND("A04054", "resource.not.found", 404, false, 0),
  /**
   * 数据已存在
   *
   * @return 处理结果
   */
  DATA_ALREADY_EXISTS("A04055", "data.already.exists", 409, false, 0),
  /**
   * 数据冲突
   *
   * @return 处理结果
   */
  DATA_CONFLICT("A04056", "data.conflict", 409, false, 0),

  // 文件相关
  /**
   * 文件上传失败
   *
   * @return 处理结果
   */
  FILE_UPLOAD_FAILED("A04061", "file.upload.failed", 500, false, 0),
  /**
   * 文件下载失败
   *
   * @return 处理结果
   */
  FILE_DOWNLOAD_FAILED("A04062", "file.download.failed", 500, false, 0),
  /**
   * 不支持的文件类型
   *
   * @return 处理结果
   */
  UNSUPPORTED_FILE_TYPE("A04063", "unsupported.file.type", 400, false, 0),
  /**
   * 文件大小超限
   *
   * @return 处理结果
   */
  FILE_SIZE_EXCEEDED("A04064", "file.size.exceeded", 400, false, 0),

  // ==================== A05 批量操作异常 ====================

  /**
   * 批量操作部分成功（HTTP 207 Multi-Status，可恢复：可重试失败的子项）
   *
   * @return 处理结果
   */
  BATCH_PARTIAL_SUCCESS("A05001", "batch.partial.success", 207, true, 0),

  // ==================== B01 系统异常 ====================

  /**
   * 系统内部错误（原 ResponseCode.INTERNAL_ERROR 100500）
   *
   * @return 处理结果
   */
  INTERNAL_ERROR("B01051", "internal.error", 500, false, 0),
  /**
   * 系统错误
   *
   * @return 处理结果
   */
  SYSTEM_ERROR("B01052", "system.error", 500, false, 0),
  /**
   * 数据库错误
   *
   * @return 处理结果
   */
  DATABASE_ERROR("B01053", "database.error", 500, false, 0),
  /**
   * 服务不可用（原 ResponseCode.SERVICE_UNAVAILABLE 100503，可恢复）
   *
   * @return 处理结果
   */
  SERVICE_UNAVAILABLE("B01054", "service.unavailable", 503, true, 30),
  /**
   * 网络错误
   *
   * @return 处理结果
   */
  NETWORK_ERROR("B01055", "network.error", 500, false, 0),
  /**
   * 缓存错误
   *
   * @return 处理结果
   */
  CACHE_ERROR("B01056", "cache.error", 500, false, 0),
  /**
   * 消息队列错误
   *
   * @return 处理结果
   */
  MQ_ERROR("B01057", "mq.error", 500, false, 0),
  /**
   * 存储错误
   *
   * @return 处理结果
   */
  STORAGE_ERROR("B01058", "storage.error", 500, false, 0),
  /**
   * 基础设施服务不可用（可恢复）
   *
   * @return 处理结果
   */
  INFRA_SERVICE_UNAVAILABLE("B01059", "infrastructure.service.unavailable", 503, true, 30),
  /**
   * 熔断器开启（可恢复：等待熔断恢复后重试）
   *
   * @return 处理结果
   */
  CIRCUIT_BREAKER_OPEN("B01060", "circuit.breaker.open", 503, true, 60),
  /**
   * 资源耗尽（可恢复：降低频率后重试）
   *
   * @return 处理结果
   */
  RESOURCE_EXHAUSTED("B01061", "resource.exhausted", 429, true, 10),
  /**
   * 服务降级（可恢复）
   *
   * @return 处理结果
   */
  SERVICE_DEGRADED("B01062", "service.degraded", 503, true, 15),

  // ==================== B02 外部服务异常 ====================

  /**
   * 网关错误（原 ResponseCode.BAD_GATEWAY 100502）
   *
   * @return 处理结果
   */
  BAD_GATEWAY("B02051", "bad.gateway", 502, false, 0),
  /**
   * 网关超时（原 ResponseCode.GATEWAY_TIMEOUT 100504）
   *
   * @return 处理结果
   */
  GATEWAY_TIMEOUT("B02052", "gateway.timeout", 504, false, 0),
  /**
   * 其他外部服务错误
   *
   * @return 处理结果
   */
  OTHER_EXTERNAL_ERROR("B02053", "other.external.error", 502, false, 0),
  /**
   * 外部服务超时
   *
   * @return 处理结果
   */
  EXTERNAL_SERVICE_TIMEOUT("B02054", "external.service.timeout", 504, false, 0),
  /**
   * 外部服务拒绝
   *
   * @return 处理结果
   */
  EXTERNAL_SERVICE_REJECTED("B02055", "external.service.rejected", 502, false, 0),

  // ==================== 幂等/通知 ====================

  /**
   * 幂等拒绝（重复提交）
   *
   * @return 处理结果
   */
  IDEMPOTENT_REJECT("A07001", "idempotent.reject", 409, false, 0),

  /**
   * 通知发送失败
   *
   * @return 处理结果
   */
  NOTIFY_ERROR("B02056", "notify.error", 500, false, 0);

  // ==================== 字段定义 ====================

  /** 异常错误码 */
  private final String code;

  /** 国际化消息键 */
  private final String key;

  /** HTTP 状态码 */
  private final int httpStatus;

  /** 是否可恢复（客户端可重试） */
  private final boolean retryable;

  /** 建议重试等待秒数 */
  private final int retryAfterSeconds;

  CoreExceptionCode(
      String code, String key, int httpStatus, boolean retryable, int retryAfterSeconds) {
    this.code = code;
    this.key = key;
    this.httpStatus = httpStatus;
    this.retryable = retryable;
    this.retryAfterSeconds = retryAfterSeconds;
  }

  @Override
  public int getHttpStatus() {
    return httpStatus;
  }

  @Override
  public boolean retryable() {
    return retryable;
  }

  @Override
  public int retryAfterSeconds() {
    return retryAfterSeconds;
  }
}
