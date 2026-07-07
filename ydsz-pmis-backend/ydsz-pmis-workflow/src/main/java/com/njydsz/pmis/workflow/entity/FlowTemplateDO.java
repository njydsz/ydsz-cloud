package com.njydsz.pmis.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 流程模板 DO
 *
 * <p>对应 pmis_flow_template 表，存储流程模板市场中的预置模板数据。
 * 每个模板包含 BPMN 2.0 XML 流程定义，支持一键导入为流程草稿，
 * 也可将已发布的流程定义导出为模板复用于跨项目/跨租户场景。
 *
 * <p>字段规范：不使用 BaseDO 审计字段（created_by/updated_by），
 * 模板为系统级预置数据，仅需 created_at / updated_at / deleted。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("pmis_flow_template")
public class FlowTemplateDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 模板编码（唯一标识，如 hr_leave_approval） */
    private String templateCode;

    /** 模板名称 */
    private String templateName;

    /** 分类：HR / FINANCE / ADMIN / PROJECT / GENERAL */
    private String category;

    /** 模板描述 */
    private String description;

    /** 图标路径 */
    private String icon;

    /** BPMN 2.0 XML 流程定义 */
    private String bpmnXml;

    /** 默认表单路径 */
    private String formPath;

    /** 使用次数 */
    private Integer useCount;

    /** 排序权重 */
    private Integer sortOrder;
}