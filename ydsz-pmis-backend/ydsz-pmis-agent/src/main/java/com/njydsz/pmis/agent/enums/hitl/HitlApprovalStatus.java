package com.njydsz.pmis.agent.enums.hitl;

/**
 * HITL（Human-in-the-Loop）审批状态枚举（P3-4 落地）
 *
 * <p>描述人工审批请求的生命周期状态，对标 LangGraph interrupt / Dify Human Feedback 机制。
 *
 * <p>状态流转：
 * <pre>
 *   PENDING ──approve──→ APPROVED   （终态：允许工具执行，恢复 ReAct 循环）
 *   PENDING ──reject───→ REJECTED   （终态：拒绝工具执行，将拒绝反馈给 LLM）
 *   PENDING ──timeout──→ TIMEOUT    （终态：超时自动关闭，视为拒绝）
 *   PENDING ──cancel───→ CANCELLED  （终态：调用方主动取消）
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-4)
 */
public enum HitlApprovalStatus {
    /** 等待审批（初始态） */
    PENDING("PENDING", "等待审批"),
    /** 已批准（终态：恢复执行工具） */
    APPROVED("APPROVED", "已批准"),
    /** 已拒绝（终态：将拒绝反馈给 LLM） */
    REJECTED("REJECTED", "已拒绝"),
    /** 已超时（终态：超过审批超时时间自动关闭） */
    TIMEOUT("TIMEOUT", "已超时"),
    /** 已取消（终态：调用方主动取消） */
    CANCELLED("CANCELLED", "已取消");

    /** 枚举编码 */
    private final String code;
    /** 枚举描述 */
    private final String desc;

    HitlApprovalStatus(String code, String desc) {
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
     * 判断当前状态是否为终态（不可再迁移）。
     *
     * @return 终态（APPROVED/REJECTED/TIMEOUT/CANCELLED）返回 true，否则 false
     */
    public boolean isTerminal() {
        return this != PENDING;
    }

    /**
     * 判断是否允许从当前状态迁移到目标状态。
     *
     * <p>PENDING 可迁移到任意终态；终态不可迁移。
     *
     * @param target 目标状态，为 null 时返回 false
     * @return 允许迁移返回 true，否则 false
     */
    public boolean canTransitTo(HitlApprovalStatus target) {
        if (target == null) return false;
        if (this == target) {
            // 终态自迁移不允许（如 APPROVED→APPROVED），非终态自迁移允许（PENDING→PENDING 等幂等场景）
            return !this.isTerminal();
        }
        if (this.isTerminal()) return false;
        // PENDING → APPROVED / REJECTED / TIMEOUT / CANCELLED
        return target.isTerminal();
    }

    /**
     * 判断审批结果是否允许工具执行。
     *
     * @return APPROVED 返回 true，其他返回 false
     */
    public boolean isApproved() {
        return this == APPROVED;
    }

    /**
     * 根据状态码解析枚举。
     *
     * @param code 状态码，大小写不敏感，为 null 时返回 null
     * @return 匹配到的枚举值；未匹配返回 null
     */
    public static HitlApprovalStatus fromCode(String code) {
        if (code == null) return null;
        for (HitlApprovalStatus s : values()) {
            if (s.code.equalsIgnoreCase(code)) return s;
        }
        return null;
    }
}
