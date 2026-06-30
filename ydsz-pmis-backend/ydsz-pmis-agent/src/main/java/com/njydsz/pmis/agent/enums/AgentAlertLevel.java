package com.njydsz.pmis.agent.enums;

/**
 * AI 预测/推荐结果风险等级
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

    public static AgentAlertLevel fromCode(String code) {
        if (code == null) return null;
        for (AgentAlertLevel s : values()) {
            if (s.code.equalsIgnoreCase(code)) return s;
        }
        return null;
    }
}
