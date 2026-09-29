package com.njydsz.common.exception.custom;

import java.util.Map;

import lombok.ToString;
import org.springframework.http.HttpStatus;

import com.njydsz.common.core.code.ResultCode;
import com.njydsz.common.exception.code.CoreExceptionCode;
import com.njydsz.common.exception.core.ExceptionInfo;
import com.njydsz.common.exception.enums.ExceptionCategory;
import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.exception.enums.ExceptionLevel;

/**
 * 基础设施异常类。
 *
 * <p>用于封装 L1/L2 工具模块（ydsz-common-util、ydsz-common-json、ydsz-common-cache、
 * ydzs-common-excel、ydsz-common-redis 等）的底层基础设施故障，如缓存加载失败、JSON 解析异常、加密算法错误、文件操作失败等。
 *
 * <p>与 {@link SysException} 的区别：
 *
 * <ul>
 *   <li>SysException 适用于 server 层系统异常</li>
 *   <li>InfraException 适用于框架/工具层基础设施异常，语义更精确</li>
 * </ul>
 *
 * <p><b>默认值：</b>
 *
 * <ul>
 *   <li>HTTP 状态码：500 Internal Server Error
 *   <li>异常级别：ERROR
 *   <li>异常分类：SYSTEM
 * </ul>
 *
 * <p><b>推荐使用方式：</b>
 *
 * <pre>{@code
 * // 1. 简单抛出
 * throw new InfraException("缓存加载失败");
 *
 * // 2. 带原始异常
 * throw new InfraException("JSON 解析失败", cause);
 *
 * // 3. 使用预定义异常码
 * throw InfraException.of(CoreExceptionCode.CACHE_ERROR);
 *
 * // 4. 完整 Builder 构建
 * throw InfraException.builder()
 *     .code("CACHE_LOAD_ERROR")
 *     .key("infra.cache.load.failed")
 *     .cause(cause)
 *     .build();
 * }</pre>
 *
 * <p><b>适用场景：</b>
 *
 * <ul>
 *   <li>ydsz-common-util：Snowflake Id 生成异常、加解密异常、脱敏异常
 *   <li>ydsz-common-json：JSON 序列化/反序列化异常
 *   <li>ydsz-common-cache：缓存加载/写入异常
 *   <li>ydsz-common-excel：Excel 读写异常
 *   <li>ydsz-common-redis：Redis 操作异常
 *   <li>ydsz-common-safe：安全防护异常（限流、签名校验失败等）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.30
 * @see CoreExceptionCode
 * @see ExceptionCategory#SYSTEM
 */
@ToString(callSuper = true)
public class InfraException extends AbstractYdszException {

  private static final long serialVersionUID = 1L;

  /** 默认 HTTP 状态码 */
  private static final int DEFAULT_HTTP_STATUS = HttpStatus.INTERNAL_SERVER_ERROR.value();

  /** 默认异常级别 */
  private static final ExceptionLevel DEFAULT_LEVEL = ExceptionLevel.ERROR;

  /** 默认异常分类 */
  private static final ExceptionCategory DEFAULT_CATEGORY = ExceptionCategory.SYSTEM;

  /** 默认错误码 */
  private static final String DEFAULT_CODE = CoreExceptionCode.INTERNAL_ERROR.getCode();

  /** 默认 i18n 消息 key */
  private static final String DEFAULT_KEY = "infra.error";

  // ==================== 核心构造函数 ====================

  /** 默认构造函数，初始化为 500 / ERROR / SYSTEM / infra.error */
  public InfraException() {
    super();
    initDefaults(DEFAULT_HTTP_STATUS, DEFAULT_LEVEL, DEFAULT_CATEGORY);
    this.code = DEFAULT_CODE;
    this.key = DEFAULT_KEY;
  }

  /**
   * 使用异常码枚举构造基础设施异常。
   *
   * @param exceptionCode 异常码枚举
   */
  public InfraException(ExceptionCode exceptionCode) {
    super();
    init(exceptionCode, new Object[] {}, DEFAULT_LEVEL, DEFAULT_CATEGORY);
  }

  /**
   * 使用异常码枚举和原始异常构造基础设施异常。
   *
   * @param exceptionCode 异常码枚举
   * @param cause 原始异常
   */
  public InfraException(ExceptionCode exceptionCode, Throwable cause) {
    super(null, cause);
    init(exceptionCode, new Object[] {}, DEFAULT_LEVEL, DEFAULT_CATEGORY);
  }

  /**
   * 使用消息构造基础设施异常（跳过 i18n 解析，直接覆盖）。
   *
   * <p>供遗留代码兼容；建议新代码使用 {@link #of(String)} 传入 i18n key，或 {@link #builder()} 完整构建。
   *
   * @param message 异常消息
   */
  public InfraException(String message) {
    super(message);
    initFields(DEFAULT_CODE, null, new Object[] {});
    initDefaults(DEFAULT_HTTP_STATUS, DEFAULT_LEVEL, DEFAULT_CATEGORY);
    this.overrideMessage = message;
  }

  /**
   * 使用消息和根因构造基础设施异常。
   *
   * @param message 异常消息
   * @param cause 根因异常
   */
  public InfraException(String message, Throwable cause) {
    super(message, cause);
    initFields(DEFAULT_CODE, null, new Object[] {});
    initDefaults(DEFAULT_HTTP_STATUS, DEFAULT_LEVEL, DEFAULT_CATEGORY);
    this.overrideMessage = message;
  }

  // ==================== 业务方法 ====================

  /**
   * 转换为可序列化的异常响应体。
   *
   * @return 新建的异常信息对象
   */
  public ExceptionInfo toExceptionInfo() {
    return buildExceptionInfo();
  }

  /**
   * 创建基础设施异常构建器。
   *
   * @return InfraExceptionBuilder 实例
   */
  public static InfraExceptionBuilder builder() {
    return new InfraExceptionBuilder();
  }

  /**
   * 通过消息 key 创建基础设施异常。
   *
   * @param key 国际化消息 key
   * @return InfraException 实例
   */
  public static InfraException of(String key) {
    InfraException exception = new InfraException();
    exception.initFields(DEFAULT_CODE, key, new Object[] {});
    return exception;
  }

  /**
   * 从预定义的异常码创建基础设施异常。
   *
   * @param exceptionCode 异常码（含 code、key、默认消息、HTTP 状态）
   * @return 携带该异常码默认配置的 InfraException 实例
   */
  public static InfraException of(ExceptionCode exceptionCode) {
    return new InfraException(exceptionCode);
  }

  /**
   * 通过错误码与消息 key 创建基础设施异常。
   *
   * @param code 业务错误码
   * @param key 国际化消息 key
   * @return InfraException 实例
   */
  public static InfraException of(String code, String key) {
    InfraException exception = new InfraException();
    exception.initFields(code, key, new Object[] {});
    return exception;
  }

  // ==================== Builder ====================

  /** 基础设施异常构建器 */
  public static class InfraExceptionBuilder extends YdszExceptionBuilder<InfraException> {

    public InfraExceptionBuilder() {
      this.code = DEFAULT_CODE;
      this.httpStatus = DEFAULT_HTTP_STATUS;
      this.level = DEFAULT_LEVEL;
      this.category = DEFAULT_CATEGORY;
    }

    /**
     * 便捷方法：设置 {@link ExceptionCode} 作为错误码。
     *
     * @param exceptionCode 异常码枚举
     * @return 处理结果
     */
    public InfraExceptionBuilder resultCode(ExceptionCode exceptionCode) {
      if (exceptionCode != null) {
        this.code = exceptionCode.getCode();
        this.key = exceptionCode.getKey();
        this.httpStatus = exceptionCode.getHttpStatus();
      }
      return this;
    }

    /**
     * 兼容路径：从 {@link ResultCode} 提取错误码。
     *
     * @param resultCode 统一结果码
     * @return 处理结果
     */
    public InfraExceptionBuilder resultCode(ResultCode resultCode) {
      if (resultCode != null) {
        this.code = resultCode.getCode();
        this.key = resultCode.getKey();
        this.httpStatus = DEFAULT_HTTP_STATUS;
      }
      return this;
    }

    /**
     * 设置国际化消息参数（变长参数版）。
     *
     * @param params 消息参数
     * @return 处理结果
     */
    @Override
    public InfraExceptionBuilder params(Object... params) {
      this.params = params;
      return this;
    }

    @Override
    public InfraExceptionBuilder code(String code) {
      this.code = code;
      return this;
    }

    @Override
    public InfraExceptionBuilder key(String key) {
      this.key = key;
      return this;
    }

    @Override
    protected InfraException doBuild(
        String code,
        String key,
        Object[] params,
        int httpStatus,
        ExceptionLevel level,
        ExceptionCategory category,
        Throwable cause,
        Map<String, Object> extData,
        String message) {
      InfraException exception = new InfraException();
      exception.initFields(code, key, params);
      if (cause != null) {
        exception.initCause(cause);
      }
      exception.setHttpStatus(httpStatus);
      exception.setLevel(level);
      exception.setCategory(category);
      exception.setExtData(extData);
      if (message != null) {
        exception.setMessage(message);
      }
      return exception;
    }
  }
}
