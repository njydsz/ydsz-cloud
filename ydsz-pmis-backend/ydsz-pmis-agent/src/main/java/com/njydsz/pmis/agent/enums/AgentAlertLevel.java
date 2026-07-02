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
    /** 提示信息 */
    INFO("INFO", "提示"),
    /** 黄色预警 */
    YELLOW("YELLOW", "黄色预警"),
    /** 红色预警（最高严重度） */
    RED("RED", "红色预警"),
    /** 正常 */
    NORMAL("NORMAL", "正常"),
    /** 推荐 */
    RECOMMEND("RECOMMEND", "推荐");

    /** 枚举编码 */
    private final String code;
    /** 枚举描述 */
    private final String desc;

    AgentAlertLevel(String code, String desc) {
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
    public static AgentAlertLevel fromCode(String code) {
        if (code == null) return null;
        for (AgentAlertLevel s : values()) {
            if (s.code.equalsIgnoreCase(code)) return s;
        }
        return null;
    }
}
