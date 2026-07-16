package com.njydsz.literule.server.approval;

import java.io.Serializable;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审批步骤定义（P1-3 多级审批流）
 *
 * <p>描述一个审批级别的完整配置，包括审批类型、所需人数、审批角色与指定审批人。
 * 一个 {@link ApprovalFlow} 由多个 ApprovalStep 按级别（level）顺序组成。
 *
 * @since 1.7.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalStep implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 级别（1, 2, 3...，从 1 开始） */
    private int level;

    /** 步骤名称（如 "一级审核"） */
    private String name;

    /** 审批类型：SINGLE（单人）/ COUNTERSIGN（会签）/ SEQUENCE（顺序） */
    private ApprovalType type;

    /**
     * COUNTERSIGN 时需要的人数
     *
     * <p>当 type=COUNTERSIGN 且 approvers 非空时，requiredCount 默认等于 approvers.size()；
     * 显式指定时以指定值为准（允许部分会签：N 人中任意 M 人通过即视为本级通过）。
     */
    private int requiredCount;

    /** 审批角色列表（权限码，如 execution:rule:approve） */
    private List<String> approverRoles;

    /** 指定审批人列表（工号；COUNTERSIGN/SEQUENCE 时使用） */
    private List<String> approvers;

    /** 是否允许委托 */
    private boolean allowDelegate;
}
