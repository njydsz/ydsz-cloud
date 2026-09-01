package com.njydsz.common.exception.custom;

import java.util.LinkedHashMap;
import java.util.Map;

import com.njydsz.common.exception.enums.ExceptionCategory;
import com.njydsz.common.exception.enums.ExceptionLevel;

/**
 * 异常构建器抽象基类
 *
 * <p>提供通用的链式构建 API，子类只需实现 {@link #doBuild} 方法。
 *
 * <p>采用简单泛型（单一类型参数 {@code T}）， setter 基类方法返回 {@code YdszExceptionBuilder<T>}，
 * 子类特有的便捷方法可直接返回子类类型以支持更灵活的链式调用。
 *
 * @param <T> 异常类型
 * @author ydsz-team
 * @since 26.09.01
 */
public abstract class YdszExceptionBuilder<T extends AbstractYdszException> {

  protected String code;
  protected String key;
  protected Object[] params;
  protected int httpStatus;
  protected ExceptionLevel level;
  protected ExceptionCategory category;
  protected Throwable cause;
  protected Map<String, Object> extData;
  protected String message;

  /** 构建阶段暂存的快照键值对，build() 后写入异常实例 */
  protected transient Map<String, String> snapshotMap;

  /**
   * 构建异常实例（由子类实现）
   *
   * @param code 错误码
   * @param key 国际化消息键
   * @param params 消息参数
   * @param httpStatus HTTP 状态码
   * @param level 异常级别
   * @param category 异常分类
   * @param cause 原始异常
   * @param extData 附加数据
   * @param message 自定义消息
   * @return 异常实例
   */
  protected abstract T doBuild(
      String code,
      String key,
      Object[] params,
      int httpStatus,
      ExceptionLevel level,
      ExceptionCategory category,
      Throwable cause,
      Map<String, Object> extData,
      String message);

  /**
   * 覆盖业务错误码。
   *
   * <p>子类构建器已在构造时预置各自的默认错误码（如业务异常为 {@code FAIL}、 系统异常为 {@code INTERNAL_ERROR}），仅在需要更精确的错误码时才调用本方法。
   *
   * @param code 业务错误码，用于前端分支处理与错误聚合；传 {@code null} 会清空预置默认值
   * @return 当前构建器，便于链式调用
   */
  public YdszExceptionBuilder<T> code(String code) {
    this.code = code;
    return this;
  }

  /**
   * 设置国际化消息键。
   *
   * <p>异常构建时只保存 key，真正的文案在 {@code getMessage()} 时按当前 Locale 解析； 未设置 key 时异常消息将退化为 {@code null}。
   *
   * @param key i18n key，需在 {@code messages.properties} 中存在，否则解析结果回退为 key 本身
   * @return 当前构建器，便于链式调用
   */
  public YdszExceptionBuilder<T> key(String key) {
    this.key = key;
    return this;
  }

  /**
   * 设置国际化消息的占位符参数。
   *
   * <p>直接持有传入数组引用，构建完成后不应再修改该数组内容。 参数顺序需与 {@code messages.properties} 中的 {@code {0}}、{@code {1}}
   * 占位符一一对应。
   *
   * @param params 消息参数数组，可为 {@code null}，构建时会被归一化为空数组
   * @return 当前构建器，便于链式调用
   */
  public YdszExceptionBuilder<T> params(Object[] params) {
    this.params = params;
    return this;
  }

  /**
   * 覆盖响应 HTTP 状态码。
   *
   * <p>用于在同一异常类型下区分语义，例如业务异常默认 400， 资源不存在场景可显式改为 404、越权场景改为 403。
   *
   * @param httpStatus HTTP 状态码；传 0 将丢失子类预置默认值，需由上层兜底
   * @return 当前构建器，便于链式调用
   */
  public YdszExceptionBuilder<T> httpStatus(int httpStatus) {
    this.httpStatus = httpStatus;
    return this;
  }

  /**
   * 覆盖异常级别。
   *
   * <p>级别决定日志打印等级与是否触发告警， 可预期的校验类失败建议降级为 WARN，避免污染错误告警。
   *
   * @param level 异常级别；传 {@code null} 会清空子类预置默认值
   * @return 当前构建器，便于链式调用
   */
  public YdszExceptionBuilder<T> level(ExceptionLevel level) {
    this.level = level;
    return this;
  }

  /**
   * 覆盖异常分类。
   *
   * <p>分类用于异常指标的维度聚合（业务/系统/第三方等）， 错误的分类会导致 SLO 统计失真，非必要不建议修改子类默认值。
   *
   * @param category 异常分类；传 {@code null} 会清空子类预置默认值
   * @return 当前构建器，便于链式调用
   */
  public YdszExceptionBuilder<T> category(ExceptionCategory category) {
    this.category = category;
    return this;
  }

  /**
   * 设置原始异常，保留完整异常链。
   *
   * <p>包装底层异常（如 SQLException、IOException）时必须设置， 否则根因堆栈会丢失，线上问题无法定位。
   *
   * @param cause 原始异常；为 {@code null} 时不建立异常链
   * @return 当前构建器，便于链式调用
   */
  public YdszExceptionBuilder<T> cause(Throwable cause) {
    this.cause = cause;
    return this;
  }

  /**
   * 设置附加业务数据。
   *
   * <p>该数据不会自动写入异常响应体，需由上层显式读取后决定是否透出； 请勿放入敏感信息，避免被日志或响应意外泄露。
   *
   * @param extData 附加数据 Map，允许为 {@code null}
   * @return 当前构建器，便于链式调用
   */
  public YdszExceptionBuilder<T> extData(Map<String, Object> extData) {
    this.extData = extData;
    return this;
  }

  /**
   * 设置自定义异常消息。
   *
   * <p>该值透传给 {@link #doBuild} 由具体子类消费：内置的 {@code BusinessExceptionBuilder} 与 {@code
   * SysExceptionBuilder} 在 {@code message} 非 null 时通过 {@link
   * AbstractYdszException#setMessage(String)} 直接覆盖消息（跳过 i18n 解析）。未设置时仍按 {@link #key(String)} 国际化解析。
   *
   * @param message 自定义消息文案，允许为 {@code null}
   * @return 当前构建器，便于链式调用
   */
  public YdszExceptionBuilder<T> message(String message) {
    this.message = message;
    return this;
  }

  /**
   * 向构建器追加单条上下文快照。
   *
   * <p>快照在 {@link #build()} 时一次性写入异常实例， 位于异常抛出位置附近记录关键上下文信息（如 orderId、userId）， 可由全局异常处理器透写入日志与响应
   * details，用于排查定位。
   *
   * <p>使用示例：
   *
   * <pre>{@code
   * throw BusinessException.builder()
   *     .key("order.create.failed")
   *     .snapshot("orderId", cmd.getOrderId())
   *     .snapshot("skuId", cmd.getSkuId())
   *     .build();
   * }</pre>
   *
   * @param key 快照键，不可为 {@code null}
   * @param value 快照值（自动 {@code String.valueOf(value)} 转换），可为 {@code null}
   * @return 当前构建器，便于链式调用
   */
  public YdszExceptionBuilder<T> snapshot(String key, Object value) {
    if (this.snapshotMap == null) {
      this.snapshotMap = new LinkedHashMap<>();
    }
    this.snapshotMap.put(key, value == null ? null : value.toString());
    return this;
  }

  /**
   * 向构建器批量追加上下文快照。
   *
   * <p>等价于多次调用 {@link #snapshot(String, Object)}。
   *
   * @param entries 待追加的键值对，可为 {@code null}
   * @return 当前构建器，便于链式调用
   * @since 26.09.01
   */
  public YdszExceptionBuilder<T> snapshots(Map<String, ?> entries) {
    if (entries == null || entries.isEmpty()) {
      return this;
    }
    if (this.snapshotMap == null) {
      this.snapshotMap = new LinkedHashMap<>(entries.size());
    }
    for (Map.Entry<String, ?> e : entries.entrySet()) {
      Object val = e.getValue();
      this.snapshotMap.put(e.getKey(), val == null ? null : val.toString());
    }
    return this;
  }

  /**
   * 构建异常实例
   *
   * @return 异常实例
   */
  public T build() {
    T exception = doBuild(code, key, params, httpStatus, level, category, cause, extData, message);
    // 暂存快照一次性写入异常实例
    if (this.snapshotMap != null && !this.snapshotMap.isEmpty()) {
      exception.setSnapshot(new LinkedHashMap<>(this.snapshotMap));
    }
    return exception;
  }
}
