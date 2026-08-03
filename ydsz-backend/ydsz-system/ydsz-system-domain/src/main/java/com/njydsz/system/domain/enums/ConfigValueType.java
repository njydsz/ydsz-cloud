package com.njydsz.system.domain.enums;

/**
 * 配置值类型枚举
 *
 * <p>用于系统配置（{@code ydsz_config}）和系统变量（{@code ydsz_variable}）的值类型校验。
 * 与 DDL CHECK 约束对齐：{@code CHECK (value_type IN ('STRING','NUMBER','BOOLEAN','JSON'))}。
 *
 * <p><b>类型说明：</b>
 * <ul>
 *   <li>{@link #STRING} — 字符串类型，原样存储 / 原样输出</li>
 *   <li>{@link #NUMBER} — 数值类型（{@code Integer / Long / BigDecimal}），写入时序列化为字符串，读取时反序列化</li>
 *   <li>{@link #BOOLEAN} — 布尔类型，存储为 {@code "true" / "false"} 字符串</li>
 *   <li>{@link #JSON} — JSON 对象 / 数组类型，写入时 {@code YdszJson.toJson}，读取时 {@code YdszJson.fromJson}</li>
 * </ul>
 *
 * <p><b>使用场景：</b>
 * <ul>
 *   <li>配置 / 变量写入前的 {@link #validate(String)} 校验</li>
 *   <li>读取时按 {@code valueType} 字段动态解析 {@code configValue} / {@code variableValue}</li>
 *   <li>前端「公开配置」接口返回时附带 {@code valueType} 提示前端按类型解析</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum ConfigValueType {

    /** 字符串类型，原样存储 / 原样输出 */
    STRING,

    /** 数值类型（{@code Integer / Long / BigDecimal}） */
    NUMBER,

    /** 布尔类型，存储为 {@code "true" / "false"} 字符串 */
    BOOLEAN,

    /** JSON 对象 / 数组类型 */
    JSON;

    /**
     * 校验值类型字符串是否合法（不区分大小写）
     *
     * <p>用于配置 / 变量写入前的合法性校验，避免脏数据落库导致后续解析失败。
     *
     * @param code 值类型字符串（{@code "STRING" / "Number" / "boolean" / "json"} 等均可，自动 {@code toUpperCase}）
     * @throws IllegalArgumentException 如果值为空或不在合法枚举范围内
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
