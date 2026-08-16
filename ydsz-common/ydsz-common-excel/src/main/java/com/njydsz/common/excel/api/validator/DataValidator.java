package com.njydsz.common.excel.api.validator;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.njydsz.common.excel.annotation.ExcelProperty;
import com.njydsz.common.excel.exception.ExcelReadException;
import com.njydsz.common.excel.support.asm.ASMFieldAccessor;
import com.njydsz.common.excel.support.asm.ASMFieldAccessor.FieldGetter;
import com.njydsz.common.excel.support.cache.ReflectCache;

/**
 * 数据验证器 — 读取数据时进行字段验证。
 *
 * <p>支持以下验证规则（通过 {@link ExcelProperty} 注解配置）：</p>
 * <ul>
 *   <li>{@code required} — 必填字段验证</li>
 *   <li>{@code maxLength} — 字符串最大长度验证</li>
 *   <li>{@code minValue / maxValue} — 数值范围验证</li>
 *   <li>{@code pattern} — 正则表达式验证</li>
 *   <li>{@code errorMessage} — 自定义验证错误消息</li>
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
 *     @ExcelProperty(value = "姓名", required = true, maxLength = 50)
 *     private String name;
 *
 *     @ExcelProperty(value = "年龄", minValue = "0", maxValue = "150")
 *     private Integer age;
 *
 *     @ExcelProperty(value = "邮箱", pattern = "^[\\w.-]+@[\\w.-]+\\.\\w+$")
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

    /** 数值转换精度：使用 String 形式避免浮点误差 */
    private static final int BYTE_TO_MB_SHIFT = 1024;

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
                validateFieldValue(annotation, fieldName, value, rowNum);
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
                collectFieldErrors(annotation, fieldName, value, rowNum, errors);
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
     * 单字段校验 —— 根据 value 类型分派到具体规则（fail-fast 路径发现错误立即抛出）。
     *
     * @param annotation 字段注解
     * @param fieldName  字段中文名
     * @param value      字段值（可能为 {@code null}）
     * @param rowNum     行号
     */
    private static void validateFieldValue(ExcelProperty annotation, String fieldName,
                                          Object value, int rowNum) {
        if (annotation.required() && value == null) {
            throw ExcelReadException.validationFailed(rowNum, fieldName, null,
                    getErrorMessage(annotation, "字段为必填项"));
        }

        if (value == null) {
            return;
        }

        validateStringRules(annotation, fieldName, value, rowNum);

        if (value instanceof Number numVal) {
            validateNumberRange(annotation, fieldName, numVal, rowNum);
        }
    }

    /**
     * 收集单字段所有校验错误到 errors 列表（collectAll 路径不立即抛出）。
     *
     * @param annotation 字段注解
     * @param fieldName  字段中文名
     * @param value      字段值（可能为 {@code null}）
     * @param rowNum     行号
     * @param errors     错误收集器
     */
    private static void collectFieldErrors(ExcelProperty annotation, String fieldName,
                                           Object value, int rowNum,
                                           List<ValidationError> errors) {
        if (annotation.required() && value == null) {
            errors.add(new ValidationError(rowNum, fieldName, null,
                    getErrorMessage(annotation, "字段为必填项")));
            return;
        }

        if (value == null) {
            return;
        }

        collectStringRuleErrors(annotation, fieldName, value, rowNum, errors);

        if (value instanceof Number numVal) {
            collectNumberRangeErrors(annotation, fieldName, numVal, rowNum, errors);
        }
    }

    /**
     * 字符串相关规则校验（fail-fast）。
     *
     * @param annotation 字段注解
     * @param fieldName  字段中文名
     * @param value      字段值（非 null）
     * @param rowNum     行号
     */
    private static void validateStringRules(ExcelProperty annotation, String fieldName,
                                            Object value, int rowNum) {
        if (!(value instanceof String strVal)) {
            return;
        }

        if (annotation.maxLength() >= 0 && strVal.length() > annotation.maxLength()) {
            throw ExcelReadException.validationFailed(rowNum, fieldName, value,
                    getErrorMessage(annotation,
                            "字段长度超过最大限制: " + strVal.length() + " > " + annotation.maxLength()));
        }

        if (!annotation.pattern().isEmpty() && !Pattern.matches(annotation.pattern(), strVal)) {
            throw ExcelReadException.validationFailed(rowNum, fieldName, value,
                    getErrorMessage(annotation, "字段值不匹配正则: " + annotation.pattern()));
        }
    }

    /**
     * 字符串相关规则校验错误收集。
     *
     * @param annotation 字段注解
     * @param fieldName  字段中文名
     * @param value      字段值（非 null）
     * @param rowNum     行号
     * @param errors     错误收集器
     */
    private static void collectStringRuleErrors(ExcelProperty annotation, String fieldName,
                                                Object value, int rowNum,
                                                List<ValidationError> errors) {
        if (!(value instanceof String strVal)) {
            return;
        }

        if (annotation.maxLength() >= 0 && strVal.length() > annotation.maxLength()) {
            errors.add(new ValidationError(rowNum, fieldName, value,
                    getErrorMessage(annotation,
                            "字段长度超过最大限制: " + strVal.length() + " > " + annotation.maxLength())));
        }

        if (!annotation.pattern().isEmpty() && !Pattern.matches(annotation.pattern(), strVal)) {
            errors.add(new ValidationError(rowNum, fieldName, value,
                    getErrorMessage(annotation, "字段值不匹配正则: " + annotation.pattern())));
        }
    }

    /**
     * 数值范围验证（fail-fast 路径）。
     *
     * @param annotation 字段注解
     * @param fieldName  字段中文名
     * @param numVal     数值（非 null）
     * @param rowNum     行号
     */
    private static void validateNumberRange(ExcelProperty annotation, String fieldName,
                                            Number numVal, int rowNum) {
        double value = numVal.doubleValue();

        if (!annotation.minValue().isEmpty()) {
            try {
                double min = Double.parseDouble(annotation.minValue());
                if (Double.compare(value, min) < 0) {
                    throw ExcelReadException.validationFailed(rowNum, fieldName, numVal,
                            getErrorMessage(annotation, "字段值小于最小值: " + value + " < " + min));
                }
            } catch (NumberFormatException e) {
                // min 值不是合法数字，跳过验证
            }
        }

        if (!annotation.maxValue().isEmpty()) {
            try {
                double max = Double.parseDouble(annotation.maxValue());
                if (Double.compare(value, max) > 0) {
                    throw ExcelReadException.validationFailed(rowNum, fieldName, numVal,
                            getErrorMessage(annotation, "字段值超过最大值: " + value + " > " + max));
                }
            } catch (NumberFormatException e) {
                // max 值不是合法数字，跳过验证
            }
        }
    }

    /**
     * 数值范围验证（collectAll 路径，累积错误而非立即抛出）。
     *
     * @param annotation 字段注解
     * @param fieldName  字段中文名
     * @param numVal     数值（非 null）
     * @param rowNum     行号
     * @param errors     错误收集器
     */
    private static void collectNumberRangeErrors(ExcelProperty annotation, String fieldName,
                                                 Number numVal, int rowNum,
                                                 List<ValidationError> errors) {
        double value = numVal.doubleValue();

        if (!annotation.minValue().isEmpty()) {
            try {
                double min = Double.parseDouble(annotation.minValue());
                if (Double.compare(value, min) < 0) {
                    errors.add(new ValidationError(rowNum, fieldName, numVal,
                            getErrorMessage(annotation, "字段值小于最小值: " + value + " < " + min)));
                }
            } catch (NumberFormatException e) {
                // min 值不是合法数字，跳过验证
            }
        }

        if (!annotation.maxValue().isEmpty()) {
            try {
                double max = Double.parseDouble(annotation.maxValue());
                if (Double.compare(value, max) > 0) {
                    errors.add(new ValidationError(rowNum, fieldName, numVal,
                            getErrorMessage(annotation, "字段值超过最大值: " + value + " > " + max)));
                }
            } catch (NumberFormatException e) {
                // max 值不是合法数字，跳过验证
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

    /**
     * 获取错误消息 —— 优先使用自定义消息。
     *
     * @param annotation     字段注解
     * @param defaultMessage 默认错误描述
     * @return 最终展示给用户的错误消息
     */
    private static String getErrorMessage(ExcelProperty annotation, String defaultMessage) {
        return annotation.errorMessage().isEmpty() ? defaultMessage : annotation.errorMessage();
    }
}
