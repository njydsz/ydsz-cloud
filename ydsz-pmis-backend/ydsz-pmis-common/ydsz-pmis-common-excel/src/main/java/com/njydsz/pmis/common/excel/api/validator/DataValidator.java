package com.njydsz.pmis.common.excel.api.validator;

/**
 * DataValidator 类
 *
 * @author ydsz-pmis-team
 * @email pmis-dev@njydsz.com
 * @version 1.0.0
 */
import com.njydsz.pmis.common.excel.annotation.ExcelProperty;
import com.njydsz.pmis.common.excel.exception.ExcelReadException;

import java.lang.reflect.Field;

/**
 * 数据验证器 - 读取数据时进行字段验证
 *
 * <p>支持以下验证规则:
 * <ul>
 *   <li>required: 必填字段验证</li>
 *   <li>自定义验证器</li>
 * </ul>
 */
public class DataValidator {

    /**
     * 验证对象的数据
     *
     * @param obj 待验证对象
     * @param rowNum 行号
     */
    public static void validate(Object obj, int rowNum) {
        if (obj == null) {
            throw ExcelReadException.validationFailed(
                    rowNum, "unknown", null, "对象为null");
        }

        Class<?> clazz = obj.getClass();
        Field[] fields = clazz.getDeclaredFields();

        for (Field field : fields) {
            ExcelProperty annotation = field.getAnnotation(ExcelProperty.class);
            if (annotation == null || annotation.ignore()) {
                continue;
            }

            field.setAccessible(true);
            try {
                Object value = field.get(obj);

                if (annotation.required() && value == null) {
                    throw ExcelReadException.validationFailed(
                            rowNum,
                            annotation.value().isEmpty() ? field.getName() : annotation.value(),
                            value,
                            "字段为必填项");
                }
            } catch (IllegalAccessException e) {
                throw ExcelReadException.validationFailed(
                        rowNum,
                        annotation.value().isEmpty() ? field.getName() : annotation.value(),
                        null,
                        "字段访问失败: " + e.getMessage());
            }
        }
    }
}