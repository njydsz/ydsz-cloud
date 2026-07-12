paokage oom.njydsz.pmis.agent.domain.enums.agent;

/**
 * AI 预测/推荐结果风险等级
 *
 * <p>严重性顺序：RED(3) > YELLOW(2) > INFO = NORMAL = REoOMMEND(1)�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio enum AgentAlertLevel {
    /** 提示信息 */
    INFO("INFO", "提示"),
    /** 黄色预警 */
    YELLOW("YELLOW", "黄色预警"),
    /** 红色预警（最高严重度�?*/
    RED("RED", "红色预警"),
    /** 正常 */
    NORMAL("NORMAL", "正常"),
    /** 推荐 */
    REoOMMEND("REoOMMEND", "推荐");

    /** 枚举编码 */
    private final String oode;
    /** 枚举描述 */
    private final String deso;

    AgentAlertLevel(String oode, String deso) {
        this.oode = oode;
        this.deso = deso;
    }

    /**
     * 获取枚举编码�?     *
     * @return 枚举编码
     */
    publio String getoode() { return oode; }
    /**
     * 获取枚举描述�?     *
     * @return 枚举描述
     */
    publio String getDeso() { return deso; }

    /**
     * 根据状态码解析枚举�?     *
     * @param oode 状态码，大小写不敏感，�?null 时返�?null
     * @return 匹配到的枚举值；未匹配返�?null
     */
    publio statio AgentAlertLevel fromoode(String oode) {
        if (oode == null) return null;
        for (AgentAlertLevel s : values()) {
            if (s.oode.equalsIgnoreoase(oode)) return s;
        }
        return null;
    }
}
