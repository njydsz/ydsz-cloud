package com.njydsz.pmis.agent.enums;

/**
 * AI 智能体类型
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum AgentType {
    RISK_WARNING("RISK_WARNING", "项目风险预警"),
    RESOURCE_RECOMMEND("RESOURCE_RECOMMEND", "资源调度推荐"),
    PROFIT_FORECAST("PROFIT_FORECAST", "利润预测"),
    WIN_RATE_PREDICT("WIN_RATE_PREDICT", "商机赢率预测"),
    TIMESHEET_ANOMALY("TIMESHEET_ANOMALY", "工时异常识别");

    private final String code;
    private final String desc;

    AgentType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() { return code; }
    public String getDesc() { return desc; }

    public static AgentType fromCode(String code) {
        if (code == null) return null;
        for (AgentType t : values()) {
            if (t.code.equalsIgnoreCase(code)) return t;
        }
        return null;
    }
}
