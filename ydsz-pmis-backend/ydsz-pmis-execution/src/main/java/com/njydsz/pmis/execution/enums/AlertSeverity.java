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

    private final String code;
    private final int weight;
    private final String desc;

    AlertSeverity(String code, int weight, String desc) {
        this.code = code;
        this.weight = weight;
        this.desc = desc;
    }

    public String getCode() { return code; }
    public int getWeight() { return weight; }
    public String getDesc() { return desc; }

    public static AlertSeverity fromCode(String code) {
        if (code == null) return null;
        for (AlertSeverity v : values()) {
            if (v.code.equalsIgnoreCase(code)) return v;
        }
        return null;
    }
}
