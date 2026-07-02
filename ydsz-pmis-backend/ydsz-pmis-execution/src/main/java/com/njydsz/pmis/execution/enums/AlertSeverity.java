package com.njydsz.pmis.execution.enums;

/**
 * 驾驶舱预警严重度
 *
 * <p>三层级：INFO（提示）/ YELLOW（黄色预警）/ RED（红色严重）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum AlertSeverity {
    INFO("INFO", 1, "提示"),
    YELLOW("YELLOW", 2, "黄色预警"),
    RED("RED", 3, "红色严重");

    /** 严重度编码（大小写不敏感） */
    private final String code;
    /** 严重度权重（数值越大越严重） */
    private final int weight;
    /** 严重度中文描述 */
    private final String desc;

    AlertSeverity(String code, int weight, String desc) {
        this.code = code;
        this.weight = weight;
        this.desc = desc;
    }

    /**
     * 获取严重度编码
     *
     * @return 严重度编码字符串
     */
    public String getCode() { return code; }

    /**
     * 获取严重度权重
     *
     * @return 严重度权重数值
     */
    public int getWeight() { return weight; }

    /**
     * 获取严重度中文描述
     *
     * @return 严重度中文描述
     */
    public String getDesc() { return desc; }

    /**
     * 根据编码反查枚举
     *
     * @param code 严重度编码（大小写不敏感）
     * @return 枚举值；未匹配返回 null
     */
    public static AlertSeverity fromCode(String code) {
        if (code == null) return null;
        for (AlertSeverity v : values()) {
            if (v.code.equalsIgnoreCase(code)) return v;
        }
        return null;
    }
}
