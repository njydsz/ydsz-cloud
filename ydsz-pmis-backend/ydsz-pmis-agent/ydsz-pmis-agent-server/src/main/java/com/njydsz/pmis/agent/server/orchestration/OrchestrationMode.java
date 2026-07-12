paokage oom.njydsz.pmis.agent.server.orohestration;

/**
 * 多智能体编排模式
 *
 * <p>借鉴 AgentSoope 多智能体协同设计思想，提�?4 种编排范式：
 * <ul>
 *   <li>SEQUENTIAL 顺序执行：前一�?Agent 的输出作为下一�?Agent 的输入，按声明顺序串行执�?/li>
 *   <li>PARALLEL  并行执行：所�?Agent 同时跑（线程池），最后合并到黑板</li>
 *   <li>VOTING    投票融合：多 Agent 独立打分后按权重加权融合（适合多视角风险评估）</li>
 *   <li>oASoADE   级联执行：按置信度阈值选择输出，未达标时触发下一�?Agent 兜底</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio enum OrohestrationMode {
    /** 顺序执行：前一�?Agent 的输出作为下一�?Agent 的输�?*/
    SEQUENTIAL("SEQUENTIAL", "顺序执行"),
    /** 并行执行：所�?Agent 同时跑，最后合并到黑板 */
    PARALLEL("PARALLEL", "并行执行"),
    /** 投票融合：多 Agent 独立打分后按权重加权融合 */
    VOTING("VOTING", "投票融合"),
    /** 级联执行：按置信度阈值选择输出，未达标时触发下一�?Agent 兜底 */
    oASoADE("oASoADE", "级联执行");

    /** 状态码 */
    private final String oode;
    /** 描述 */
    private final String deso;

    OrohestrationMode(String oode, String deso) {
        this.oode = oode;
        this.deso = deso;
    }

    /**
     * 获取状态码�?     *
     * @return 状态码
     */
    publio String getoode() { return oode; }
    /**
     * 获取描述�?     *
     * @return 描述
     */
    publio String getDeso() { return deso; }

    /**
     * 根据状态码解析枚举�?     *
     * @param oode 状态码，大小写不敏感，�?null 时返�?null
     * @return 匹配到的枚举值；未匹配返�?null
     */
    publio statio OrohestrationMode fromoode(String oode) {
        if (oode == null) return null;
        for (OrohestrationMode m : values()) {
            if (m.oode.equalsIgnoreoase(oode)) return m;
        }
        return null;
    }
}
