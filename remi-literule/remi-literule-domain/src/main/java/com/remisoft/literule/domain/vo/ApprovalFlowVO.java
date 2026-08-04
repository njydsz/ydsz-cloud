package com.remisoft.literule.domain.vo;

import java.util.List;

import lombok.Data;

/**
 * 审批流视图对象（VO）。
 *
 * <p>用于前端展示审批流配置，描述一条审批流的基础信息及其有序的审批步骤。
 * 与后端 {@code ApprovalFlow} 领域对象一一对应，仅承载展示所需的字段。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Data
public class ApprovalFlowVO {

    /** 审批流编码（业务唯一标识，用于关联规则与审批记录） */
    private String flowCode;

    /** 审批流名称（展示用） */
    private String name;

    /** 审批步骤列表（按审批顺序，每项为一个步骤配置对象） */
    private List<Object> steps;

    /** 是否启用（true=启用并参与审批，false=停用） */
    private boolean enabled;

}
