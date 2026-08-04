package com.remisoft.common.excel.api.validator;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.regex.Pattern;

import com.remisoft.common.excel.annotation.ExcelProperty;
import com.remisoft.common.excel.exception.ExcelReadException;
import com.remisoft.common.excel.support.asm.ASMFieldAccessor;
import com.remisoft.common.excel.support.asm.ASMFieldAccessor.FieldGetter;
import com.remisoft.common.excel.support.cache.ReflectCache;

/**
 * 数据验证器 — 读取数据时进行字段验证
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
 * }</pre>
 *
 * @author remi-team
 * @since 1.0.0
 */
public class DataValidator {

    private DataValidator() {
    }

    /**
     * 验证对象的数据
     *
     * @param obj    待验证对象
     * @param rowNum 行号
     */
    public static void validate(Object obj, int rowNum) {
        if (obj == null) {
            throw ExcelReadException.validationFailed(rowNum, "unknown", null, "对象为null");
        }

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

                // 1. 必填验证
                if (annotation.required() && value == null) {
                    throw ExcelReadException.validationFailed(rowNum, fieldName, value,
                            getErrorMessage(annotation, "字段为必填项"));
                }

                if (value == null) {
                    continue;
                }

                // 2. 字符串最大长度验证
                if (annotation.maxLength() >= 0 && value instanceof String strVal) {
                    if (strVal.length() > annotation.maxLength()) {
                        throw ExcelReadException.validationFailed(rowNum, fieldName, value,
                                getErrorMessage(annotation,
                                        "字段长度超过最大限制: " + strVal.length() + " > " + annotation.maxLength()));
                    }
                }

                // 3. 正则表达式验证
                if (!annotation.pattern().isEmpty() && value instanceof String strVal) {
                    if (!Pattern.matches(annotation.pattern(), strVal)) {
                        throw ExcelReadException.validationFailed(rowNum, fieldName, value,
                                getErrorMessage(annotation, "字段值不匹配正则: " + annotation.pattern()));
                    }
                }

                // 4. 数值范围验证
                if (value instanceof Number numVal) {
                    validateNumberRange(annotation, fieldName, numVal, rowNum);
                }

            } catch (ExcelReadException e) {
                throw e;
            } catch (Exception e) {
                throw ExcelReadException.validationFailed(rowNum, fieldName, null,
                        "字段访问失败: " + e.getMessage());
            }
        }
    }

    /**
     * 数值范围验证
     */
    private static void validateNumberRange(ExcelProperty annotation, String fieldName,
                                            Number numVal, int rowNum) {
        BigDecimal value = new BigDecimal(numVal.toString());

        if (!annotation.minValue().isEmpty()) {
            try {
                BigDecimal min = new BigDecimal(annotation.minValue());
                if (value.compareTo(min) < 0) {
                    throw ExcelReadException.validationFailed(rowNum, fieldName, numVal,
                            getErrorMessage(annotation, "字段值小于最小值: " + value + " < " + min));
                }
            } catch (NumberFormatException e) {
                // min 值不是合法数字，跳过验证
            }
        }

        if (!annotation.maxValue().isEmpty()) {
            try {
                BigDecimal max = new BigDecimal(annotation.maxValue());
                if (value.compareTo(max) > 0) {
                    throw ExcelReadException.validationFailed(rowNum, fieldName, numVal,
                            getErrorMessage(annotation, "字段值超过最大值: " + value + " > " + max));
                }
            } catch (NumberFormatException e) {
                // max 值不是合法数字，跳过验证
            }
        }
    }

    /**
     * 获取错误消息：优先使用自定义消息
     */
    private static String getErrorMessage(ExcelProperty annotation, String defaultMessage) {
        return annotation.errorMessage().isEmpty() ? defaultMessage : annotation.errorMessage();
    }
}
