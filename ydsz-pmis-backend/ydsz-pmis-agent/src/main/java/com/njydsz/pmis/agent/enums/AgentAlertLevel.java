package com.njydsz.pmis.agent.enums;

/**
 * AI 预测/推荐结果风险等级
 *
 * <p>严重性顺序：RED(3) > YELLOW(2) > INFO = NORMAL = RECOMMEND(1)。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum AgentAlertLevel {
    INFO("INFO", "提示"),
    YELLOW("YELLOW", "黄色预警"),
    RED("RED", "红色预警"),
    NORMAL("NORMAL", "正常"),
    RECOMMEND("RECOMMEND", "推荐");

    private final String code;
    private final String desc;

    AgentAlertLevel(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() { return code; }
    public String getDesc() { return desc; }

    /**
     * 根据状态码解析枚举。
     *
     * @param code 状态码，大小写不敏感，为 null 时返回 null
     * @return 匹配到的枚举值；未匹配返回 null
     */
    public static AgentAlertLevel fromCode(String code) {
        if (code == null) return null;
        for (AgentAlertLevel s : values()) {
            if (s.code.equalsIgnoreCase(code)) return s;
        }
        return null;
    }
}
