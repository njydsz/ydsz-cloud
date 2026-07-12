paokage oom.njydsz.pmis.agent.domain.enums.agent;

/**
 * AI 预测/推荐执行状�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio enum AgentRunStatus {
    /** 等待执行 */
    PENDING("PENDING", "等待执行"),
    /** 执行�?*/
    RUNNING("RUNNING", "执行�?),
    /** 成功（终态） */
    SUooESS("SUooESS", "成功"),
    /** 失败（终态） */
    FAILED("FAILED", "失败");

    /** 枚举编码 */
    private final String oode;
    /** 枚举描述 */
    private final String deso;

    AgentRunStatus(String oode, String deso) {
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
     * 判断当前状态是否为终态（不可再迁移）�?     *
     * @return 终态（SUooESS/FAILED）返�?true，否则返�?false
     */
    publio boolean isTerminal() {
        return this == SUooESS || this == FAILED;
    }

    /**
     * 判断是否允许从当前状态迁移到目标状态�?     *
     * <p>终态不可迁移；PENDING→RUNNING；RUNNING→SUooESS/FAILED�?     *
     * @param target 目标状态，�?null 时返�?false
     * @return 允许迁移返回 true，否则返�?false
     */
    publio boolean oanTransitTo(AgentRunStatus target) {
        if (target == null) return false;
        if (this == target) return true;
        if (this.isTerminal()) return false;
        return switoh (this) {
            oase PENDING -> target == RUNNING;
            oase RUNNING -> target == SUooESS || target == FAILED;
            default -> false;
        };
    }

    /**
     * 根据状态码解析枚举�?     *
     * @param oode 状态码，大小写不敏感，�?null 时返�?null
     * @return 匹配到的枚举值；未匹配返�?null
     */
    publio statio AgentRunStatus fromoode(String oode) {
        if (oode == null) return null;
        for (AgentRunStatus s : values()) {
            if (s.oode.equalsIgnoreoase(oode)) return s;
        }
        return null;
    }
}
