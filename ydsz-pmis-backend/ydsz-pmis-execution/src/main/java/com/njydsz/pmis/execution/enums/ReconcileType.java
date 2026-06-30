package com.njydsz.pmis.execution.enums;

/**
 * 对账(Reconcile)校验类型
 *
 * <p>财务-工时数据交叉验证场景。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum ReconcileType {

    /** 工时已 APPROVED 但缺失成本归集(漏算) */
    MISSING_COST_FOR_APPROVED_TIME,

    /** 工时已 REJECTED 但存在成本归集(幽灵成本) */
    GHOST_COST_FOR_REJECTED_TIME,

    /** 单人单日工时超过 24h(数据异常) */
    DAILY_HOURS_OVERFLOW,

    /** 单人单周工时超过 60h(过载) */
    WEEKLY_HOURS_OVERLOAD,

    /** 跨项目冲突: 同一员工同一天在多个项目填写工时 */
    CROSS_PROJECT_CONFLICT,

    /** 成本归集金额与 工时 × 费率 偏差超过容忍度 */
    AMOUNT_DRIFT,

    /** 成本已分配(allocated=1)但工时仍非 APPROVED */
    ALLOCATED_BEFORE_APPROVAL;

    private final String code;
    private final String desc;

    ReconcileType() {
        this.code = name();
        this.desc = name();
    }

    public String getCode() { return code; }
    public String getDesc() { return desc; }

    public static ReconcileType fromCode(String code) {
        if (code == null) return null;
        for (ReconcileType t : values()) {
            if (t.code.equals(code)) return t;
        }
        return null;
    }
}
