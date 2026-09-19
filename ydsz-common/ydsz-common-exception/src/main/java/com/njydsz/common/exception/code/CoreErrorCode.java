package com.njydsz.common.exception.code;

import java.util.HashMap;
import java.util.Map;

import lombok.Getter;

import com.njydsz.common.core.code.ResultCode;
import com.njydsz.common.exception.enums.ExceptionCategory;
import com.njydsz.common.exception.enums.ExceptionLevel;

/**
 * 核心模块错误码静态引用（编译时安全访问，26.09.19 新增）。
 *
 * <p>业务代码通过静态字段 {@link #PARAM_ERROR} 引用枚举常量 {@link
 * CoreExceptionCode#PARAM_ERROR}，获得编译期类型安全检查与 IDE 自动补全， 杜绝手写字面量 {@code
 * "A01052"} / {@code "param.error"} 导致的拼写错误。
 *
 * <p><b>使用方式：</b>
 *
 * <pre>{@code
 * // ✅ 正确：编译时安全引用
 * throw BusinessException.of(CoreErrorCode.PARAM_ERROR);
 *
 * // ❌ 禁止：手写字面量
 * throw BusinessException.builder().code("A01052").key("param.error").build();
 * }</pre>
 *
 * <p><b>与枚举的关系：</b>本类所有字段委托给 {@link CoreExceptionCode} 枚举：启动时 {@link
 * com.njydsz.common.exception.registry.ExceptionCodeScanner} 自动将枚举注册到统一错误码表。 本类的存在仅是为了让业务层在不直接 import 枚举的情况下获得静态引用能力。
 *
 * <p><b>扩展规则：</b>每次在 {@link CoreExceptionCode} 中新增码常量后，必须同步在此类中添加对应的
 * 静态引用字段，保证枚举与静态门面的一致性（启动时 {@link #validateConsistency()} 校验）。
 *
 * @author ydsz-team
 * @since 26.09.19
 * @see CoreExceptionCode
 * @see ErrorCodeTable
 */
@Getter
public enum CoreErrorCode implements ResultCode {

  // ======================== 成功 ========================

  /** 操作成功 */
  SUCCESS(CoreExceptionCode.SUCCESS),

  // ======================== A01 参数/业务异常 ========================

  /** 操作失败 */
  FAIL(CoreExceptionCode.FAIL),
  /** 参数错误 */
  PARAM_ERROR(CoreExceptionCode.PARAM_ERROR),
  /** 非法参数 */
  ILLEGAL_ARGUMENT(CoreExceptionCode.ILLEGAL_ARGUMENT),
  /** 请求格式无效 */
  INVALID_REQUEST_FORMAT(CoreExceptionCode.INVALID_REQUEST_FORMAT),
  /** 业务状态无效 */
  INVALID_BUSINESS_STATE(CoreExceptionCode.INVALID_BUSINESS_STATE),
  /** 业务规则违反 */
  BUSINESS_RULE_VIOLATION(CoreExceptionCode.BUSINESS_RULE_VIOLATION),
  /** 通用业务错误 */
  BUSINESS_ERROR(CoreExceptionCode.BUSINESS_ERROR),
  /** 请求方法不允许 */
  METHOD_NOT_ALLOWED(CoreExceptionCode.METHOD_NOT_ALLOWED),
  /** 重复提交 */
  DUPLICATE_SUBMISSION(CoreExceptionCode.DUPLICATE_SUBMISSION),
  /** 流程状态无效 */
  INVALID_FLOW_STATE(CoreExceptionCode.INVALID_FLOW_STATE),
  /** 乐观锁冲突 */
  OPTIMISTIC_LOCK_CONFLICT(CoreExceptionCode.OPTIMISTIC_LOCK_CONFLICT),
  /** 唯一约束冲突 */
  UNIQUE_CONSTRAINT_VIOLATION(CoreExceptionCode.UNIQUE_CONSTRAINT_VIOLATION),
  /** 外键约束违反 */
  FOREIGN_KEY_VIOLATION(CoreExceptionCode.FOREIGN_KEY_VIOLATION),
  /** 非空约束违反 */
  NOT_NULL_VIOLATION(CoreExceptionCode.NOT_NULL_VIOLATION),
  /** 检查约束违反 */
  CHECK_CONSTRAINT_VIOLATION(CoreExceptionCode.CHECK_CONSTRAINT_VIOLATION),

  // ======================== A04 数据/资源异常 ========================

  /** 资源不存在 */
  NOT_FOUND(CoreExceptionCode.NOT_FOUND),
  /** 资源冲突 */
  CONFLICT(CoreExceptionCode.CONFLICT),
  /** 数据未找到 */
  DATA_NOT_FOUND(CoreExceptionCode.DATA_NOT_FOUND),
  /** 资源未找到 */
  RESOURCE_NOT_FOUND(CoreExceptionCode.RESOURCE_NOT_FOUND),
  /** 数据已存在 */
  DATA_ALREADY_EXISTS(CoreExceptionCode.DATA_ALREADY_EXISTS),
  /** 数据冲突 */
  DATA_CONFLICT(CoreExceptionCode.DATA_CONFLICT),

  // ======================== A04 文件相关 ========================

  /** 文件上传失败 */
  FILE_UPLOAD_FAILED(CoreExceptionCode.FILE_UPLOAD_FAILED),
  /** 文件下载失败 */
  FILE_DOWNLOAD_FAILED(CoreExceptionCode.FILE_DOWNLOAD_FAILED),
  /** 不支持的文件类型 */
  UNSUPPORTED_FILE_TYPE(CoreExceptionCode.UNSUPPORTED_FILE_TYPE),
  /** 文件大小超限 */
  FILE_SIZE_EXCEEDED(CoreExceptionCode.FILE_SIZE_EXCEEDED),

  // ======================== A05 批量操作异常 ========================

  /** 批量操作部分成功 */
  BATCH_PARTIAL_SUCCESS(CoreExceptionCode.BATCH_PARTIAL_SUCCESS),

  // ======================== A07 幂等通知 ========================

  /** 幂等拒绝 */
  IDEMPOTENT_REJECT(CoreExceptionCode.IDEMPOTENT_REJECT),

  // ======================== B01 系统异常 ========================

  /** 系统内部错误 */
  INTERNAL_ERROR(CoreExceptionCode.INTERNAL_ERROR),
  /** 系统错误 */
  SYSTEM_ERROR(CoreExceptionCode.SYSTEM_ERROR),
  /** 数据库错误 */
  DATABASE_ERROR(CoreExceptionCode.DATABASE_ERROR),
  /** 服务不可用 */
  SERVICE_UNAVAILABLE(CoreExceptionCode.SERVICE_UNAVAILABLE),
  /** 网络错误 */
  NETWORK_ERROR(CoreExceptionCode.NETWORK_ERROR),
  /** 缓存错误 */
  CACHE_ERROR(CoreExceptionCode.CACHE_ERROR),
  /** 消息队列错误 */
  MQ_ERROR(CoreExceptionCode.MQ_ERROR),
  /** 存储错误 */
  STORAGE_ERROR(CoreExceptionCode.STORAGE_ERROR),
  /** 基础设施服务不可用 */
  INFRA_SERVICE_UNAVAILABLE(CoreExceptionCode.INFRA_SERVICE_UNAVAILABLE),
  /** 熔断器开启 */
  CIRCUIT_BREAKER_OPEN(CoreExceptionCode.CIRCUIT_BREAKER_OPEN),
  /** 资源耗尽 */
  RESOURCE_EXHAUSTED(CoreExceptionCode.RESOURCE_EXHAUSTED),
  /** 服务降级 */
  SERVICE_DEGRADED(CoreExceptionCode.SERVICE_DEGRADED),

  // ======================== B02 外部服务异常 ========================

  /** 网关错误 */
  BAD_GATEWAY(CoreExceptionCode.BAD_GATEWAY),
  /** 网关超时 */
  GATEWAY_TIMEOUT(CoreExceptionCode.GATEWAY_TIMEOUT),
  /** 其他外部服务错误 */
  OTHER_EXTERNAL_ERROR(CoreExceptionCode.OTHER_EXTERNAL_ERROR),
  /** 外部服务超时 */
  EXTERNAL_SERVICE_TIMEOUT(CoreExceptionCode.EXTERNAL_SERVICE_TIMEOUT),
  /** 外部服务拒绝 */
  EXTERNAL_SERVICE_REJECTED(CoreExceptionCode.EXTERNAL_SERVICE_REJECTED),
  /** 通知发送失败 */
  NOTIFY_ERROR(CoreExceptionCode.NOTIFY_ERROR);

  // ======================== 字段与委托 ========================

  /** 委托的目标枚举常量 */
  private final CoreExceptionCode delegate;

  /** 构造核心错误码引用 */
  CoreErrorCode(CoreExceptionCode delegate) {
    this.delegate = delegate;
  }

  /**
   * 获取错误码字符串
   *
   * @return 错误码字符串
   */
  @Override
  public String getCode() {
    return delegate.getCode();
  }

  /**
   * 获取 i18n 消息键
   *
   * @return i18n 消息键
   */
  @Override
  public String getKey() {
    return delegate.getKey();
  }

  /**
   * 获取错误消息（i18n key 本身作为兜底）
   *
   * @return 兜底消息
   */
  @Override
  public String getMsg() {
    return delegate.getKey();
  }

  /**
   * 获取 HTTP 状态码
   *
   * @return HTTP 状态码
   */
  public int httpStatus() {
    return delegate.getHttpStatus();
  }

  /**
   * 获取异常分类
   *
   * @return 异常分类
   */
  public ExceptionCategory category() {
    return delegate.getCategory();
  }

  /**
   * 获取异常级别
   *
   * @return 异常级别
   */
  public ExceptionLevel level() {
    return delegate.getLevel();
  }

  /**
   * 是否可恢复（客户端是否可重试）
   *
   * @return 是否可重试
   */
  public boolean retryable() {
    return delegate.retryable();
  }

  /**
   * 获取建议重试等待秒数
   *
   * @return 等待秒数
   */
  public int retryAfterSeconds() {
    return delegate.retryAfterSeconds();
  }

  // ======================== 一致性校验 ========================

  /** 枚举-门面一致性校验缓存 */
  private static volatile Boolean consistencyCheckResult;

  /**
   * 校验枚举与静态门面的一致性（启动时调用一次，测试环境立即暴露问题）。
   *
   * <p>确保 {@link CoreExceptionCode} 中每个常量都有对应的 {@link CoreErrorCode} 字段，反之亦然。
   * 不一致时抛出 IllegalStateException 阻止应用启动。
   */
  public static synchronized void validateConsistency() {
    if (consistencyCheckResult != null && consistencyCheckResult) {
      return;
    }
    CoreExceptionCode[] enumValues = CoreExceptionCode.values();
    CoreErrorCode[] facadeValues = CoreErrorCode.values();

    if (enumValues.length != facadeValues.length) {
      throw new IllegalStateException(
          String.format(
              "CoreExceptionCode 枚举数量 (%d) 与 CoreErrorCode 门面数量 (%d) 不一致，"
                  + "请在两者之间同步增减。",
              enumValues.length, facadeValues.length));
    }

    Map<String, CoreErrorCode> facadeMap = new HashMap<>(facadeValues.length);
    for (CoreErrorCode facade : facadeValues) {
      facadeMap.put(facade.getCode(), facade);
    }

    StringBuilder missing = new StringBuilder();
    for (CoreExceptionCode enumValue : enumValues) {
      if (!facadeMap.containsKey(enumValue.getCode())) {
        missing.append("  - missing facade for enum: ").append(enumValue.name()).append("\n");
      }
    }

    if (!missing.isEmpty()) {
      throw new IllegalStateException(
          "CoreErrorCode 静态门面缺少以下 CoreExceptionCode 枚举引用的字段:\n" + missing);
    }

    consistencyCheckResult = Boolean.TRUE;
  }
}
