package com.njydsz.pmis.workflow.server.template;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * 流程模板定义（P2-1 流程模板市场）
 *
 * <p>预置常用审批流程模板，用户可基于模板快速创建流程定义。
 * 对标钉钉/飞书审批的"模板市场"功能。
 *
 * <p>模板分类：
 * <ul>
 *   <li>人事：请假/加班/出差/报销/入职/离职</li>
 *   <li>财务：费用报销/采购申请/付款申请/预算调整</li>
 *   <li>行政：用印申请/会议室预定/办公用品领用</li>
 *   <li>项目：立项申请/变更申请/验收申请/结项申请</li>
 * </ul>
 *
 * @since 1.9.0
 */
@Data
public class FlowTemplateDefinition implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 模板编码（唯一标识） */
    private String templateCode;

    /** 模板名称 */
    private String templateName;

    /** 模板分类（HR/FINANCE/ADMIN/PROJECT/OTHER） */
    private String category;

    /** 模板描述 */
    private String description;

    /** 模板图标 URL */
    private String iconUrl;

    /** 模板排序权重（越小越靠前） */
    private Integer sortOrder;

    /** 适用场景说明 */
    private String useCase;

    /** 节点定义列表（JSON 格式，可直接部署为流程定义） */
    private List<Map<String, Object>> nodes;

    /** 连线定义列表（JSON 格式） */
    private List<Map<String, Object>> skips;

    /** 表单 Schema（JSON 格式） */
    private Map<String, Object> formSchema;

    /** 默认流程变量 */
    private Map<String, Object> defaultVariables;

    /** 是否为系统内置模板（true 不可删除） */
    private Boolean systemBuiltIn;

    /** 标签列表（用于搜索过滤） */
    private List<String> tags;
}
