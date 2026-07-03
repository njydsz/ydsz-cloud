package com.njydsz.pmis.agent.enums;

/**
 * AI 智能体类型
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum AgentType {
    /** 项目风险预警 */
    RISK_WARNING("RISK_WARNING", "项目风险预警"),
    /** 资源调度推荐 */
    RESOURCE_RECOMMEND("RESOURCE_RECOMMEND", "资源调度推荐"),
    /** 利润预测 */
    PROFIT_FORECAST("PROFIT_FORECAST", "利润预测"),
    /** 商机赢率预测 */
    WIN_RATE_PREDICT("WIN_RATE_PREDICT", "商机赢率预测"),
    /** 工时异常识别 */
    TIMESHEET_ANOMALY("TIMESHEET_ANOMALY", "工时异常识别"),
    /** P2-1: 审批人推荐（流程引擎） */
    APPROVER_RECOMMEND("APPROVER_RECOMMEND", "审批人推荐"),
    /** P2-1: 意见起草（流程引擎） */
    COMMENT_DRAFT("COMMENT_DRAFT", "意见起草"),
    /** P0-3: AI 一句话生成流程 */
    FLOW_GENERATOR("FLOW_GENERATOR", "流程生成");

    /** 枚举编码 */
    private final String code;
    /** 枚举描述 */
    private final String desc;

    AgentType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 获取枚举编码。
     *
     * @return 枚举编码
     */
    public String getCode() { return code; }
    /**
     * 获取枚举描述。
     *
     * @return 枚举描述
     */
    public String getDesc() { return desc; }

    /**
     * 根据状态码解析枚举。
     *
     * @param code 状态码，大小写不敏感，为 null 时返回 null
     * @return 匹配到的枚举值；未匹配返回 null
     */
    public static AgentType fromCode(String code) {
        if (code == null) return null;
        for (AgentType t : values()) {
            if (t.code.equalsIgnoreCase(code)) return t;
        }
        return null;
    }
}
