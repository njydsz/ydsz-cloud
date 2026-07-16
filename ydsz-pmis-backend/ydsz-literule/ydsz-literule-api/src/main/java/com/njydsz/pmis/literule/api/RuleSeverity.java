package com.njydsz.literule.api;

/**
 * 规则严重度枚举
 *
 * <p>三层级：INFO（提示）/ YELLOW（黄色预警）/ RED（红色严重）。
 * 与 execution 模块 AlertSeverity 语义对齐，支持getCode/fromCode 互转。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum RuleSeverity {
    INFO("INFO", 1, "提示"),
    YELLOW("YELLOW", 2, "黄色预警"),
    RED("RED", 3, "红色严重");

    private final String code;
    private final int weight;
    private final String desc;

    RuleSeverity(String code, int weight, String desc) {
        this.code = code;
        this.weight = weight;
        this.desc = desc;
    }

    public String getCode() { return code; }
    public int getWeight() { return weight; }
    public String getDesc() { return desc; }

    /**
     * 根据编码反查枚举（大小写不敏感）
     *
     * @param code 严重度编码
     * @return 枚举值；未匹配返回 null
     */
    public static RuleSeverity fromCode(String code) {
        if (code == null) return null;
        for (RuleSeverity v : values()) {
            if (v.code.equalsIgnoreCase(code)) return v;
        }
        return null;
    }
}
