package com.njydsz.common.excel.api.validator;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.njydsz.common.excel.annotation.ExcelProperty;
import com.njydsz.common.excel.exception.ExcelReadException;
import com.njydsz.common.excel.support.asm.ASMFieldAccessor;
import com.njydsz.common.excel.support.asm.ASMFieldAccessor.FieldGetter;
import com.njydsz.common.excel.support.cache.ReflectCache;

/**
 * 数据验证器 — 读取数据时基于 JSR-303 标准注解进行字段验证。
 *
 * <p>支持以下校验规则（通过 Jakarta Bean Validation 注解配置）：</p>
 * <ul>
 *   <li>{@link NotNull} — 必填字段验证</li>
 *   <li>{@link Size#max()} — 字符串最大长度验证</li>
 *   <li>{@link Min} / {@link Max} — 数值范围验证</li>
 *   <li>{@link jakarta.validation.constraints.Pattern} — 正则表达式验证</li>
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

    private DataValidator() {
    }

    /**
     * 校验模式。
     */
    public enum ValidationMode {

        /**
         * 快速失败模式 —— 遇到第一个校验错误立即抛出异常。
         */
        FAIL_FAST,

        /**
         * 全量收集模式 —— 收集该行所有字段的校验错误后一次性抛出。
         *
         * <p>异常消息中包含全部失败字段名与错误描述，适合需要一次性展示所有错误给用户的场景。
         */
        COLLECT_ALL
    }

    /**
     * 单条校验错误详情。
     */
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
     * @param obj    待验证对象
     * @param rowNum 行号（从 1 开始，用于错误提示）
     */
    public static void validate(Object obj, int rowNum) {
        validate(obj, rowNum, ValidationMode.FAIL_FAST);
    }

    /**
     * 校验对象的数据。
     *
     * @param obj    待验证对象
     * @param rowNum 行号（从 1 开始，用于错误提示）
     * @param mode   校验模式（不能为 {@code null}）
     */
    public static void validate(Object obj, int rowNum, ValidationMode mode) {
        if (mode == null) {
            throw new IllegalArgumentException("ValidationMode must not be null");
        }
        if (obj == null) {
            throw ExcelReadException.validationFailed(rowNum, "unknown", null, "对象为null");
        }

        if (mode == ValidationMode.COLLECT_ALL) {
            validateCollectAll(obj, rowNum);
        } else {
            validateFailFast(obj, rowNum);
        }
    }

    /**
     * 快速失败校验。
     *
     * @param obj    待验证对象
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
                throw ExcelReadException.validationFailed(rowNum, fieldName, null,
                        "字段访问失败: " + e.getMessage());
            }
        }
    }

    /**
     * 全量收集校验 —— 积累所有字段的校验错误后一次性抛出。
     *
     * <p>即使某个字段校验失败，仍会继续校验其余字段，最终将所有失败详情合并到一条异常中。
     *
     * @param obj    待验证对象
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
                errors.add(new ValidationError(rowNum, fieldName, null,
                        "字段访问失败: " + e.getMessage()));
            }
        }

        if (!errors.isEmpty()) {
            throw buildCollectAllException(rowNum, errors);
        }
    }

    /**
     * 单字段校验 —— 根据 JSR-303 注解分派到具体规则（fail-fast 路径发现错误立即抛出）。
     *
     * @param field     字段对象
     * @param fieldName 字段中文名
     * @param value     字段值（可能为 {@code null}）
     * @param rowNum    行号
     */
    private static void validateFieldValue(Field field, String fieldName,
                                           Object value, int rowNum) {
        // @NotNull 必填校验
        if (field.isAnnotationPresent(NotNull.class) && value == null) {
            NotNull notNull = field.getAnnotation(NotNull.class);
            throw ExcelReadException.validationFailed(rowNum, fieldName, null,
                    notNull.message());
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
     * @param field     字段对象
     * @param fieldName 字段中文名
     * @param value     字段值（可能为 {@code null}）
     * @param rowNum    行号
     * @param errors    错误收集器
     */
    private static void collectFieldErrors(Field field, String fieldName,
                                            Object value, int rowNum,
                                            List<ValidationError> errors) {
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
     * @param field     字段对象
     * @param fieldName 字段中文名
     * @param value     字段值（非 null）
     * @param rowNum    行号
     */
    private static void validateStringRules(Field field, String fieldName,
                                             Object value, int rowNum) {
        if (!(value instanceof String strVal)) {
            return;
        }

        // @Size(max = N) 最大长度校验
        if (field.isAnnotationPresent(Size.class)) {
            Size size = field.getAnnotation(Size.class);
            if (strVal.length() > size.max()) {
                throw ExcelReadException.validationFailed(rowNum, fieldName, value,
                        size.message());
            }
        }

        // @Pattern(regexp = "...") 正则校验
        if (field.isAnnotationPresent(jakarta.validation.constraints.Pattern.class)) {
            jakarta.validation.constraints.Pattern pattern =
                    field.getAnnotation(jakarta.validation.constraints.Pattern.class);
            if (!Pattern.matches(pattern.regexp(), strVal)) {
                throw ExcelReadException.validationFailed(rowNum, fieldName, value,
                        pattern.message());
            }
        }
    }

    /**
     * 字符串相关规则校验错误收集。
     *
     * @param field     字段对象
     * @param fieldName 字段中文名
     * @param value     字段值（非 null）
     * @param rowNum    行号
     * @param errors    错误收集器
     */
    private static void collectStringRuleErrors(Field field, String fieldName,
                                                 Object value, int rowNum,
                                                 List<ValidationError> errors) {
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
        if (field.isAnnotationPresent(jakarta.validation.constraints.Pattern.class)) {
            jakarta.validation.constraints.Pattern pattern =
                    field.getAnnotation(jakarta.validation.constraints.Pattern.class);
            if (!Pattern.matches(pattern.regexp(), strVal)) {
                errors.add(new ValidationError(rowNum, fieldName, value, pattern.message()));
            }
        }
    }

    /**
     * 数值范围验证（fail-fast 路径）。
     *
     * @param field     字段对象
     * @param fieldName 字段中文名
     * @param numVal    数值（非 null）
     * @param rowNum    行号
     */
    private static void validateNumberRange(Field field, String fieldName,
                                            Number numVal, int rowNum) {
        double value = numVal.doubleValue();

        // @Min(value = N) 最小值校验
        if (field.isAnnotationPresent(Min.class)) {
            Min min = field.getAnnotation(Min.class);
            if (Double.compare(value, min.value()) < 0) {
                throw ExcelReadException.validationFailed(rowNum, fieldName, numVal,
                        min.message());
            }
        }

        // @Max(value = N) 最大值校验
        if (field.isAnnotationPresent(Max.class)) {
            Max max = field.getAnnotation(Max.class);
            if (Double.compare(value, max.value()) > 0) {
                throw ExcelReadException.validationFailed(rowNum, fieldName, numVal,
                        max.message());
            }
        }
    }

    /**
     * 数值范围验证（collectAll 路径，累积错误而非立即抛出）。
     *
     * @param field     字段对象
     * @param fieldName 字段中文名
     * @param numVal    数值（非 null）
     * @param rowNum    行号
     * @param errors    错误收集器
     */
    private static void collectNumberRangeErrors(Field field, String fieldName,
                                                 Number numVal, int rowNum,
                                                 List<ValidationError> errors) {
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
    private static ExcelReadException buildCollectAllException(int rowNum,
                                                               List<ValidationError> errors) {
        StringBuilder sb = new StringBuilder("第 ").append(rowNum).append(" 行校验失败，共 ")
                .append(errors.size()).append(" 处错误: ");
        for (int i = 0; i < errors.size(); i++) {
            if (i > 0) {
                sb.append("; ");
            }
            sb.append(errors.get(i).toString());
        }
        return ExcelReadException.validationFailed(rowNum, "multiple", null, sb.toString());
    }
}
