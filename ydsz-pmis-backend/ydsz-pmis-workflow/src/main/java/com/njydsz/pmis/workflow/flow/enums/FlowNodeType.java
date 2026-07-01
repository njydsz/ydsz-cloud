package com.njydsz.pmis.workflow.flow.enums;

/**
 * 流程节点类型
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum FlowNodeType {

    /** 开始节点 */
    START(0, "开始"),
    /** 审批节点（单人审批） */
    APPROVAL(1, "审批"),
    /** 抄送节点 */
    CC(2, "抄送"),
    /** 条件路由节点（互斥网关） */
    CONDITION(3, "条件"),
    /** 并行网关（同时推进多条分支） */
    PARALLEL(4, "并行网关"),
    /** 包容网关（满足条件的分支都推进） */
    INCLUSIVE(5, "包容网关"),
    /** 结束节点 */
    END(6, "结束"),
    /** 子流程节点 */
    SUBPROCESS(7, "子流程");

    private final int code;
    private final String desc;

    FlowNodeType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static FlowNodeType of(Integer code) {
        if (code == null) {
            return APPROVAL;
        }
        for (FlowNodeType t : values()) {
            if (t.code == code) {
                return t;
            }
        }
        return APPROVAL;
    }
}
