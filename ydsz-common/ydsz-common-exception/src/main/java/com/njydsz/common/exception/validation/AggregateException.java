package com.njydsz.common.exception.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.ToString;
import org.springframework.http.HttpStatus;

import com.njydsz.common.exception.code.CoreExceptionCode;
import com.njydsz.common.exception.core.ExceptionInfo;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.exception.enums.ExceptionCategory;
import com.njydsz.common.exception.enums.ExceptionLevel;

/**
 * 聚合校验异常 — 单次请求收集多个校验错误（26.09.19 新增）。
 *
 * <p>适用于批处理校验、表单多字段联合校验、批量导入等需要一次性报告多个错误的场景。
 * 对标 Spring {@link
 * org.springframework.validation.BindException} 与 Zod {@code ZodError.issues[]}。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * AggregateException agg = AggregateException.of("user.create.validation");
 * agg.addError("username", "user.username.required", "用户名不能为空");
 * agg.addError("email", "user.email.invalid", "邮箱格式不正确");
 * agg.addError("age", "user.age.range", "年龄需在 1-150 之间");
 * if (agg.hasErrors()) {
 *     throw agg;
 * }
 * }</pre>
 *
 * <p><b>响应格式：</b>
 *
 * <pre>{@code
 * {
 *   "code": "A01052",
 *   "key": "user.create.validation",
 *   "message": "参数校验失败: 3 个错误",
 *   "details": {
 *     "errors": [
 *       {"field": "username", "code": "user.username.required", "message": "用户名不能为空"},
 *       {"field": "email", "code": "user.email.invalid", "message": "邮箱格式不正确"}
 *     ]
 *   }
 * }
 * }</pre>
 *
 * <p>默认 HTTP 状态码 400，级别 WARN（校验拦截为主，通常无需弹窗告警）。
 *
 * @author ydsz-team
 * @since 26.09.19
 * @see BusinessException
 */
@Getter
@ToString(callSuper = true)
public class AggregateException extends BusinessException {

  private static final long serialVersionUID = 1L;

  /** 错误摘要 i18n key（用于 {@link #getMessage()} 返回聚合摘要） */
  private static final String DEFAULT_KEY = "aggregate.validation.failed";

  /** 错误项列表（有序插入） */
  private final List<ErrorItem> errors = new ArrayList<>(4);

  /**
   * 聚合错误项。
   *
   * @param field 标识校验失败的字段名（表单路径 / 行号 / 对象键），不可为 {@code null}
   * @param code 该字段的错误码（业务码或 i18n key）
   * @param message 字段的校验失败消息（已解析或原始文案）
   */
  public record ErrorItem(String field, String code, String message) {}

  /**
   * 构造聚合校验异常（使用默认错误码 A01052 PARAM_ERROR）。
   */
  public AggregateException() {
    super(CoreExceptionCode.PARAM_ERROR);
  }

  /**
   * 构造聚合校验异常（指定 i18n 摘要 key）。
   *
   * @param key 聚合摘要 i18n 消息键
   */
  public AggregateException(String key) {
    super();
    initFields(CoreExceptionCode.PARAM_ERROR.getCode(), key, new Object[] {});
    initDefaults(
        HttpStatus.BAD_REQUEST.value(), ExceptionLevel.WARN, ExceptionCategory.BUSINESS);
  }

  /**
   * 静态工厂方法 - 创建聚合校验异常。
   *
   * @param key 聚合摘要 i18n 消息键（可为 {@code null}，使用默认 {@value #DEFAULT_KEY}）
   * @return 新的聚合校验异常实例
   */
  public static AggregateException of(String key) {
    return new AggregateException(key != null ? key : DEFAULT_KEY);
  }

  /**
   * 静态工厂方法 - 创建聚合校验异常（使用默认 key）。
   *
   * @return 新的聚合校验异常实例
   */
  public static AggregateException of() {
    return of(DEFAULT_KEY);
  }

  /**
   * 追加一条校验错误。
   *
   * @param field 校验失败的字段名
   * @param code 错误码
   * @param message 错误消息
   */
  public void addError(String field, String code, String message) {
    errors.add(new ErrorItem(field, code, message));
  }

  /**
   * 追加一条校验错误（无自定义错误码，仅字段 + 消息）。
   *
   * @param field 校验失败的字段名
   * @param message 错误消息
   */
  public void addError(String field, String message) {
    errors.add(new ErrorItem(field, null, message));
  }

  /**
   * 批量追加校验错误。
   *
   * @param errorMap 字段名 → 错误消息 映射；为 {@code null} 时忽略
   */
  public void addErrors(Map<String, String> errorMap) {
    if (errorMap == null) {
      return;
    }
    for (Map.Entry<String, String> entry : errorMap.entrySet()) {
      errors.add(new ErrorItem(entry.getKey(), null, entry.getValue()));
    }
  }

  /**
   * 是否包含校验错误。
   *
   * @return true — 至少一条校验错误
   */
  public boolean hasErrors() {
    return !errors.isEmpty();
  }

  /**
   * 获取校验错误数量。
   *
   * @return 错误数量
   */
  public int errorCount() {
    return errors.size();
  }

  /**
   * 获取不可变的校验错误列表视图。
   *
   * @return 错误列表
   */
  public List<ErrorItem> getErrors() {
    return Collections.unmodifiableList(errors);
  }

  /**
   * 构建包含 errors 详情的 {@link ExceptionInfo}。
   *
   * <p>与基类 {@link BusinessException#toExceptionInfo()} 不同，此处将 {@link #errors} 列表写入 details 供前端展示。
   *
   * @return 包含聚合错误详情的 {@link ExceptionInfo}
   */
  @Override
  public ExceptionInfo toExceptionInfo() {
    ExceptionInfo info = super.toExceptionInfo();
    // 将 errors 写入 details，供前端逐字段展示
    Map<String, Object> details = new LinkedHashMap<>(4);
    details.put("errors", getErrors());
    info.setDetails(details);
    return info;
  }

  /**
   * 获取聚合摘要消息。
   *
   * <p>若通过 {@link #addError} 追加了错误，返回 "key前缀 + 错误数" 摘要；
   * 若未设置 key，返回 {@link com.njydsz.common.exception.code.CoreExceptionCode#PARAM_ERROR} 对应消息。
   *
   * @return 国际化摘要解析结果
   */
  @Override
  public String getMessage() {
    if (errors.isEmpty()) {
      return super.getMessage();
    }
    // 有校验错误时返回带错误数的摘要
    String base = super.getMessage();
    return base + " (" + errors.size() + " errors)";
  }
}
