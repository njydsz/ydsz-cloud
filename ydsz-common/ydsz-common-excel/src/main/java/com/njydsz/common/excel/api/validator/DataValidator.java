package com.njydsz.common.excel.api.validator;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.njydsz.common.excel.annotation.ExcelProperty;
import com.njydsz.common.excel.exception.ExcelReadException;
import com.njydsz.common.excel.support.asm.ASMFieldAccessor;
import com.njydsz.common.excel.support.asm.ASMFieldAccessor.FieldGetter;
import com.njydsz.common.excel.support.cache.ReflectCache;

/**
 * 数据验证器 — 读取数据时基于 JSR-303 标准注解进行字段验证。
 *
 * <p><b>双路径设计（P1-3 修复）：</b></p>
 *
 * <ul>
 *   <li><b>标准委托路径（优先）</b>：classpath 存在 Jakarta Bean Validation 实现（如
 *       Hibernate Validator）时，委托标准 {@link Validator} 校验——覆盖全部标准约束
 *       （{@code @NotBlank}/{@code @Email} 等）与自定义约束，此前内置规则对这类约束
 *       <b>静默放行</b>。标准路径校验对象的全部约束（含非 Excel 映射字段），
 *       {@code @ExcelProperty.value()} 仅用于错误提示中的字段友好名。</li>
 *   <li><b>内置规则回退路径</b>：实现缺席时（L1 层不强制依赖校验实现），回退到手搓的
 *       五种规则（{@link NotNull} / {@link Size#max()} / {@link Min} / {@link Max} /
 *       {@link Pattern}），仅校验带 {@code @ExcelProperty} 的字段。</li>
 * </ul>
 *
 * <p>支持两种校验模式（通过 {@link ValidationMode} 选择）：</p>
 * <ul>
 *   <li>{@link ValidationMode#FAIL_FAST} — 遇到第一个校验失败立即抛出（默认）</li>
 *   <li>{@link ValidationMode#COLLECT_ALL} — 收集该行全部字段校验失败后一次性抛出，
 *       异常消息中包含所有失败字段详情</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * public class UserDTO {
 *     @ExcelProperty(value = "姓名", order = 1)
 *     @NotNull(message = "姓名不能为空")
 *     @Size(max = 50, message = "姓名长度不能超过50")
 *     private String name;
 *
 *     @ExcelProperty(value = "年龄", order = 2)
 *     @Min(value = 0, message = "年龄不能小于0")
 *     @Max(value = 150, message = "年龄不能大于150")
 *     private Integer age;
 *
 *     @ExcelProperty(value = "邮箱", order = 3)
 *     @jakarta.validation.constraints.Pattern(
 *         regexp = "^[\\w.-]+@[\\w.-]+\\.\\w+$",
 *         message = "邮箱格式不正确"
 *     )
 *     private String email;
 * }
 *
 * // fail-fast 模式（默认）
 * DataValidator.validate(user, 5, ValidationMode.FAIL_FAST);
 *
 * // collect-all 模式
 * DataValidator.validate(user, 5, ValidationMode.COLLECT_ALL);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class DataValidator {

  private DataValidator() {}

  /** 校验模式。 */
  public enum ValidationMode {

    /** 快速失败模式 —— 遇到第一个校验错误立即抛出异常。 */
    FAIL_FAST,

    /**
     * 全量收集模式 —— 收集该行所有字段的校验错误后一次性抛出。
     *
     * <p>异常消息中包含全部失败字段名与错误描述，适合需要一次性展示所有错误给用户的场景。
     */
    COLLECT_ALL
  }

  /** 单条校验错误详情。 */
  public static class ValidationError {

    /** Excel 行号（从 1 开始） */
    private final int rowNum;

    /** 字段中文名（若未配置则为 Java 字段名） */
    private final String fieldName;

    /** 字段实际值（可能为 null） */
    private final Object value;

    /** 错误描述 */
    private final String message;

    public ValidationError(int rowNum, String fieldName, Object value, String message) {
      this.rowNum = rowNum;
      this.fieldName = fieldName;
      this.value = value;
      this.message = message;
    }

    public int getRowNum() {
      return rowNum;
    }

    public String getFieldName() {
      return fieldName;
    }

    public Object getValue() {
      return value;
    }

    public String getMessage() {
      return message;
    }

    @Override
    public String toString() {
      return fieldName + ": " + message + " (value=" + value + ")";
    }
  }

  /**
   * 校验对象的数据（默认 FAIL_FAST 模式）。
   *
   * @param obj 待验证对象
   * @param rowNum 行号（从 1 开始，用于错误提示）
   */
  public static void validate(Object obj, int rowNum) {
    validate(obj, rowNum, ValidationMode.FAIL_FAST);
  }

  /**
   * 校验对象的数据。
   *
   * @param obj 待验证对象
   * @param rowNum 行号（从 1 开始，用于错误提示）
   * @param mode 校验模式（不能为 {@code null}）
   */
  public static void validate(Object obj, int rowNum, ValidationMode mode) {
    if (mode == null) {
      throw new IllegalArgumentException("ValidationMode must not be null");
    }
    if (obj == null) {
      throw ExcelReadException.validationFailed(rowNum, "unknown", null, "对象为null");
    }

    // P1-3：优先委托标准 Validator（覆盖 @NotBlank/@Email/自定义约束等全部规则）；
    // 实现缺席时回退内置五规则，保持 L1 层零强制依赖
    Validator standard = StandardValidatorHolder.INSTANCE;
    if (standard != null) {
      validateWithStandard(standard, obj, rowNum, mode);
      return;
    }

    if (mode == ValidationMode.COLLECT_ALL) {
      validateCollectAll(obj, rowNum);
    } else {
      validateFailFast(obj, rowNum);
    }
  }

  /** 标准校验器懒加载持有者——仅首次校验时解析，实现缺席时保持 null。 */
  private static final class StandardValidatorHolder {

    static final Validator INSTANCE = resolveStandardValidator();

    private static Validator resolveStandardValidator() {
      try {
        // 工厂为进程级单例，随模块生命周期存活（Spring 环境下业务方也可自行装配 Validator）
        return Validation.buildDefaultValidatorFactory().getValidator();
      } catch (Throwable t) {
        // jakarta.validation 实现缺席（validation-api 为 optional 依赖）——回退内置规则
        return null;
      }
    }
  }

  /**
   * 标准 Validator 委托校验。
   *
   * <p>校验对象的全部约束（含非 {@code @ExcelProperty} 字段）；错误提示字段名优先取
   * {@code @ExcelProperty.value()}（Excel 列名），否则用 Java 字段名。
   *
   * @param <T> 对象类型
   * @param validator 标准校验器
   * @param obj 待验证对象
   * @param rowNum 行号
   * @param mode 校验模式
   */
  private static <T> void validateWithStandard(
      Validator validator, T obj, int rowNum, ValidationMode mode) {
    Set<ConstraintViolation<T>> violations = validator.validate(obj);
    if (violations.isEmpty()) {
      return;
    }

    Class<?> clazz = obj.getClass();
    if (mode == ValidationMode.COLLECT_ALL) {
      List<ValidationError> errors = new ArrayList<>(violations.size());
      for (ConstraintViolation<T> violation : violations) {
        errors.add(toValidationError(violation, clazz, rowNum));
      }
      throw buildCollectAllException(rowNum, errors);
    }

    ConstraintViolation<T> first = violations.iterator().next();
    ValidationError error = toValidationError(first, clazz, rowNum);
    throw ExcelReadException.validationFailed(
        rowNum, error.getFieldName(), error.getValue(), error.getMessage());
  }

  /**
   * 将标准 ConstraintViolation 转换为 ValidationError（字段名带 Excel 列名友好映射）。
   *
   * @param <T> 对象类型
   * @param violation 约束违例
   * @param clazz 对象类型（用于字段名映射）
   * @param rowNum 行号
   * @return 转换后的错误详情
   */
  private static <T> ValidationError toValidationError(
      ConstraintViolation<T> violation, Class<?> clazz, int rowNum) {
    String propertyPath = violation.getPropertyPath().toString();
    return new ValidationError(
        rowNum, resolveFieldName(clazz, propertyPath), violation.getInvalidValue(),
        violation.getMessage());
  }

  /**
   * 解析错误提示字段名：取属性路径末节点对应的 Java 字段，优先映射 {@code @ExcelProperty.value()}。
   *
   * @param clazz 对象类型
   * @param propertyPath 约束违例的属性路径
   * @return 友好字段名
   */
  private static String resolveFieldName(Class<?> clazz, String propertyPath) {
    String leaf = leafName(propertyPath);
    for (Field field : ReflectCache.getCachedFields(clazz)) {
      if (field.getName().equals(leaf)) {
        ExcelProperty annotation = field.getAnnotation(ExcelProperty.class);
        if (annotation != null && !annotation.value().isEmpty()) {
          return annotation.value();
        }
        return leaf;
      }
    }
    return propertyPath;
  }

  /** 取属性路径的末节点名（如 "address.city" → "city"）。 */
  private static String leafName(String propertyPath) {
    if (propertyPath == null || propertyPath.isEmpty()) {
      return "";
    }
    int lastDot = propertyPath.lastIndexOf('.');
    return lastDot >= 0 ? propertyPath.substring(lastDot + 1) : propertyPath;
  }

  /**
   * 快速失败校验。
   *
   * @param obj 待验证对象
   * @param rowNum 行号
   */
  private static void validateFailFast(Object obj, int rowNum) {
    Class<?> clazz = obj.getClass();
    Field[] fields = ReflectCache.getCachedFields(clazz);

    for (Field field : fields) {
      ExcelProperty annotation = field.getAnnotation(ExcelProperty.class);
      if (annotation == null || annotation.ignore()) {
        continue;
      }

      String fieldName = annotation.value().isEmpty() ? field.getName() : annotation.value();
      FieldGetter getter = ASMFieldAccessor.getGetter(clazz, field);

      try {
        Object value = getter.get(obj);
        validateFieldValue(field, fieldName, value, rowNum);
      } catch (ExcelReadException e) {
        throw e;
      } catch (Exception e) {
        throw ExcelReadException.validationFailed(
            rowNum, fieldName, null, "字段访问失败: " + e.getMessage());
      }
    }
  }

  /**
   * 全量收集校验 —— 积累所有字段的校验错误后一次性抛出。
   *
   * <p>即使某个字段校验失败，仍会继续校验其余字段，最终将所有失败详情合并到一条异常中。
   *
   * @param obj 待验证对象
   * @param rowNum 行号
   */
  private static void validateCollectAll(Object obj, int rowNum) {
    Class<?> clazz = obj.getClass();
    Field[] fields = ReflectCache.getCachedFields(clazz);
    List<ValidationError> errors = new ArrayList<>();

    for (Field field : fields) {
      ExcelProperty annotation = field.getAnnotation(ExcelProperty.class);
      if (annotation == null || annotation.ignore()) {
        continue;
      }

      String fieldName = annotation.value().isEmpty() ? field.getName() : annotation.value();
      FieldGetter getter = ASMFieldAccessor.getGetter(clazz, field);

      try {
        Object value = getter.get(obj);
        collectFieldErrors(field, fieldName, value, rowNum, errors);
      } catch (Exception e) {
        errors.add(new ValidationError(rowNum, fieldName, null, "字段访问失败: " + e.getMessage()));
      }
    }

    if (!errors.isEmpty()) {
      throw buildCollectAllException(rowNum, errors);
    }
  }

  /**
   * 单字段校验 —— 根据 JSR-303 注解分派到具体规则（fail-fast 路径发现错误立即抛出）。
   *
   * @param field 字段对象
   * @param fieldName 字段中文名
   * @param value 字段值（可能为 {@code null}）
   * @param rowNum 行号
   */
  private static void validateFieldValue(Field field, String fieldName, Object value, int rowNum) {
    // @NotNull 必填校验
    if (field.isAnnotationPresent(NotNull.class) && value == null) {
      NotNull notNull = field.getAnnotation(NotNull.class);
      throw ExcelReadException.validationFailed(rowNum, fieldName, null, notNull.message());
    }

    if (value == null) {
      return;
    }

    validateStringRules(field, fieldName, value, rowNum);

    if (value instanceof Number numVal) {
      validateNumberRange(field, fieldName, numVal, rowNum);
    }
  }

  /**
   * 收集单字段所有校验错误到 errors 列表（collectAll 路径不立即抛出）。
   *
   * @param field 字段对象
   * @param fieldName 字段中文名
   * @param value 字段值（可能为 {@code null}）
   * @param rowNum 行号
   * @param errors 错误收集器
   */
  private static void collectFieldErrors(
      Field field, String fieldName, Object value, int rowNum, List<ValidationError> errors) {
    // @NotNull 必填校验
    if (field.isAnnotationPresent(NotNull.class) && value == null) {
      NotNull notNull = field.getAnnotation(NotNull.class);
      errors.add(new ValidationError(rowNum, fieldName, null, notNull.message()));
      return;
    }

    if (value == null) {
      return;
    }

    collectStringRuleErrors(field, fieldName, value, rowNum, errors);

    if (value instanceof Number numVal) {
      collectNumberRangeErrors(field, fieldName, numVal, rowNum, errors);
    }
  }

  /**
   * 字符串相关规则校验（fail-fast）。
   *
   * @param field 字段对象
   * @param fieldName 字段中文名
   * @param value 字段值（非 null）
   * @param rowNum 行号
   */
  private static void validateStringRules(Field field, String fieldName, Object value, int rowNum) {
    if (!(value instanceof String strVal)) {
      return;
    }

    // @Size(max = N) 最大长度校验
    if (field.isAnnotationPresent(Size.class)) {
      Size size = field.getAnnotation(Size.class);
      if (strVal.length() > size.max()) {
        throw ExcelReadException.validationFailed(rowNum, fieldName, value, size.message());
      }
    }

    // @Pattern(regexp = "...") 正则校验
    if (field.isAnnotationPresent(Pattern.class)) {
      Pattern pattern =
          field.getAnnotation(Pattern.class);
      if (!strVal.matches(pattern.regexp())) {
        throw ExcelReadException.validationFailed(rowNum, fieldName, value, pattern.message());
      }
    }
  }

  /**
   * 字符串相关规则校验错误收集。
   *
   * @param field 字段对象
   * @param fieldName 字段中文名
   * @param value 字段值（非 null）
   * @param rowNum 行号
   * @param errors 错误收集器
   */
  private static void collectStringRuleErrors(
      Field field, String fieldName, Object value, int rowNum, List<ValidationError> errors) {
    if (!(value instanceof String strVal)) {
      return;
    }

    // @Size(max = N) 最大长度校验
    if (field.isAnnotationPresent(Size.class)) {
      Size size = field.getAnnotation(Size.class);
      if (strVal.length() > size.max()) {
        errors.add(new ValidationError(rowNum, fieldName, value, size.message()));
      }
    }

    // @Pattern(regexp = "...") 正则校验
    if (field.isAnnotationPresent(Pattern.class)) {
      Pattern pattern =
          field.getAnnotation(Pattern.class);
      if (!strVal.matches(pattern.regexp())) {
        errors.add(new ValidationError(rowNum, fieldName, value, pattern.message()));
      }
    }
  }

  /**
   * 数值范围验证（fail-fast 路径）。
   *
   * @param field 字段对象
   * @param fieldName 字段中文名
   * @param numVal 数值（非 null）
   * @param rowNum 行号
   */
  private static void validateNumberRange(
      Field field, String fieldName, Number numVal, int rowNum) {
    double value = numVal.doubleValue();

    // @Min(value = N) 最小值校验
    if (field.isAnnotationPresent(Min.class)) {
      Min min = field.getAnnotation(Min.class);
      if (Double.compare(value, min.value()) < 0) {
        throw ExcelReadException.validationFailed(rowNum, fieldName, numVal, min.message());
      }
    }

    // @Max(value = N) 最大值校验
    if (field.isAnnotationPresent(Max.class)) {
      Max max = field.getAnnotation(Max.class);
      if (Double.compare(value, max.value()) > 0) {
        throw ExcelReadException.validationFailed(rowNum, fieldName, numVal, max.message());
      }
    }
  }

  /**
   * 数值范围验证（collectAll 路径，累积错误而非立即抛出）。
   *
   * @param field 字段对象
   * @param fieldName 字段中文名
   * @param numVal 数值（非 null）
   * @param rowNum 行号
   * @param errors 错误收集器
   */
  private static void collectNumberRangeErrors(
      Field field, String fieldName, Number numVal, int rowNum, List<ValidationError> errors) {
    double value = numVal.doubleValue();

    // @Min(value = N) 最小值校验
    if (field.isAnnotationPresent(Min.class)) {
      Min min = field.getAnnotation(Min.class);
      if (Double.compare(value, min.value()) < 0) {
        errors.add(new ValidationError(rowNum, fieldName, numVal, min.message()));
      }
    }

    // @Max(value = N) 最大值校验
    if (field.isAnnotationPresent(Max.class)) {
      Max max = field.getAnnotation(Max.class);
      if (Double.compare(value, max.value()) > 0) {
        errors.add(new ValidationError(rowNum, fieldName, numVal, max.message()));
      }
    }
  }

  /**
   * 构建 collectAll 模式下的汇总异常。
   *
   * @param rowNum 行号
   * @param errors 收集到的错误列表（非空）
   * @return 聚合了所有错误描述的 {@link ExcelReadException}
   */
  private static ExcelReadException buildCollectAllException(
      int rowNum, List<ValidationError> errors) {
    StringBuilder sb =
        new StringBuilder("第 ")
            .append(rowNum)
            .append(" 行校验失败，共 ")
            .append(errors.size())
            .append(" 处错误: ");
    for (int i = 0; i < errors.size(); i++) {
      if (i > 0) {
        sb.append("; ");
      }
      sb.append(errors.get(i).toString());
    }
    return ExcelReadException.validationFailed(rowNum, "multiple", null, sb.toString());
  }
}
