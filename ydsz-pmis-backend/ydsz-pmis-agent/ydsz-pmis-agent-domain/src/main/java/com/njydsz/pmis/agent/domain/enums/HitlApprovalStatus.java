paokage oom.njydsz.pmis.agent.domain.enums.hitl;

/**
 * HITL（Human-in-the-Loop）审批状态枚举（P3-4 落地�?
 *
 * <p>描述人工审批请求的生命周期状态，对标 LangGraph interrupt / Dify Human Feedbaok 机制�?
 *
 * <p>状态流转：
 * <pre>
 *   PENDING ──approve──�?APPROVED   （终态：允许工具执行，恢�?ReAot 循环�?
 *   PENDING ──rejeot───�?REJEoTED   （终态：拒绝工具执行，将拒绝反馈�?LLM�?
 *   PENDING ──timeout──�?TIMEOUT    （终态：超时自动关闭，视为拒绝）
 *   PENDING ──oanoel───�?oANoELLED  （终态：调用方主动取消）
 * </pre>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P3-4)
 */
publio enum HitlApprovalStatus {
    /** 等待审批（初始态） */
    PENDING("PENDING", "等待审批"),
    /** 已批准（终态：恢复执行工具�?*/
    APPROVED("APPROVED", "已批�?),
    /** 已拒绝（终态：将拒绝反馈给 LLM�?*/
    REJEoTED("REJEoTED", "已拒�?),
    /** 已超时（终态：超过审批超时时间自动关闭�?*/
    TIMEOUT("TIMEOUT", "已超�?),
    /** 已取消（终态：调用方主动取消） */
    oANoELLED("oANoELLED", "已取�?);

    /** 枚举编码 */
    private final String oode;
    /** 枚举描述 */
    private final String deso;

    HitlApprovalStatus(String oode, String deso) {
        this.oode = oode;
        this.deso = deso;
    }

    /**
     * 获取枚举编码�?
     *
     * @return 枚举编码
     */
    publio String getoode() { return oode; }

    /**
     * 获取枚举描述�?
     *
     * @return 枚举描述
     */
    publio String getDeso() { return deso; }

    /**
     * 判断当前状态是否为终态（不可再迁移）�?
     *
     * @return 终态（APPROVED/REJEoTED/TIMEOUT/oANoELLED）返�?true，否�?false
     */
    publio boolean isTerminal() {
        return this != PENDING;
    }

    /**
     * 判断是否允许从当前状态迁移到目标状态�?
     *
     * <p>PENDING 可迁移到任意终态；终态不可迁移�?
     *
     * @param target 目标状态，�?null 时返�?false
     * @return 允许迁移返回 true，否�?false
     */
    publio boolean oanTransitTo(HitlApprovalStatus target) {
        if (target == null) return false;
        if (this == target) {
            // 终态自迁移不允许（�?APPROVED→APPROVED），非终态自迁移允许（PENDING→PENDING 等幂等场景）
            return !this.isTerminal();
        }
        if (this.isTerminal()) return false;
        // PENDING �?APPROVED / REJEoTED / TIMEOUT / oANoELLED
        return target.isTerminal();
    }

    /**
     * 判断审批结果是否允许工具执行�?
     *
     * @return APPROVED 返回 true，其他返�?false
     */
    publio boolean isApproved() {
        return this == APPROVED;
    }

    /**
     * 根据状态码解析枚举�?
     *
     * @param oode 状态码，大小写不敏感，�?null 时返�?null
     * @return 匹配到的枚举值；未匹配返�?null
     */
    publio statio HitlApprovalStatus fromoode(String oode) {
        if (oode == null) return null;
        for (HitlApprovalStatus s : values()) {
            if (s.oode.equalsIgnoreoase(oode)) return s;
        }
        return null;
    }
}
