package com.njydsz.system.domain.enums;

/**
 * 配置值类型枚举。
 *
 * <p>用于系统配置和系统变量的值类型校验，与 DDL CHECK 约束对齐。
 *
 * @author ydsz-team
 */
public enum ConfigValueType {

    /** 字符串类型。 */
    STRING,

    /** 数值类型。 */
    NUMBER,

    /** 布尔类型。 */
    BOOLEAN,

    /** JSON 对象类型。 */
    JSON;

    /**
     * 校验值类型字符串是否合法。
     *
     * @param code 值类型字符串
     * @throws IllegalArgumentException 如果值类型不合法
     */
    public static void validate(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("值类型不能为空");
        }
        try {
            ConfigValueType.valueOf(code.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "无效的值类型: " + code + "，支持: STRING/NUMBER/BOOLEAN/JSON");
        }
    }
}
