package com.njydsz.pmis.literule.server.approval;

/**
 * 审批类型（P1-3 多级审批流）
 *
 * <p>定义单个审批步骤的通过策略：
 * <ul>
 *   <li>{@link #SINGLE} - 单人审批，任一有权限者通过即进入下一级</li>
 *   <li>{@link #COUNTERSIGN} - 会签，所有指定审批人都需通过才进入下一级</li>
 *   <li>{@link #SEQUENCE} - 顺序审批，按 approvers 列表顺序依次审批</li>
 * </ul>
 *
 * @since 1.7.0
 */
public enum ApprovalType {

    /** 单人审批：任一有权限者通过即可进入下一级 */
    SINGLE,

    /** 会签：所有指定审批人都需通过才进入下一级 */
    COUNTERSIGN,

    /** 顺序审批：按 approvers 列表顺序依次审批 */
    SEQUENCE
}
