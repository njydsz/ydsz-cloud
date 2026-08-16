package com.njydsz.common.util.diff;

import java.io.Serializable;
import lombok.Data;

/**
 * 字段差异记录
 *
 * <p>记录单个字段在更新操作前后的值变化，用于生成操作日志的变更详情。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class FieldDiff implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 字段 Java 名称 */
    private final String fieldName;

    /** 字段中文名称（展示用） */
    private final String fieldLabel;

    /** 变更前值（已脱敏、已格式化） */
    private final String oldValue;

    /** 变更后值（已脱敏、已格式化） */
    private final String newValue;

    /** 是否为敏感字段 */
    private final boolean sensitive;

    /**
     * 创建字段差异记录
     *
     * @param fieldName  字段 Java 名称
     * @param fieldLabel 字段中文名称
     * @param oldValue   变更前值
     * @param newValue   变更后值
     * @param sensitive  是否为敏感字段
     * @return 字段差异记录
     */
    public static FieldDiff of(String fieldName, String fieldLabel, String oldValue, String newValue, boolean sensitive) {
        return new FieldDiff(fieldName, fieldLabel, oldValue, newValue, sensitive);
    }

    /**
     * 生成可读的差异描述
     *
     * @return 格式如 "用户名: 张三 → 李四"
     */
    public String toReadableString() {
        return fieldLabel + ": " + (oldValue == null ? "(空)" : oldValue) + " → " + (newValue == null ? "(空)" : newValue);
    }
}
