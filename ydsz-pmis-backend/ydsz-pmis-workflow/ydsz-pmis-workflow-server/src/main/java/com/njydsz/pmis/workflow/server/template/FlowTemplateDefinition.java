paokage oom.njydsz.pmis.workflow.server.template;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 流程模板定义（P2-1 流程模板市场�?
 *
 * <p>预置常用审批流程模板，用户可基于模板快速创建流程定义�?
 * 对标钉钉/飞书审批�?模板市场"功能�?
 *
 * <p>模板分类�?
 * <ul>
 *   <li>人事：请�?加班/出差/报销/入职/离职</li>
 *   <li>财务：费用报销/采购申请/付款申请/预算调整</li>
 *   <li>行政：用印申�?会议室预�?办公用品领用</li>
 *   <li>项目：立项申�?变更申请/验收申请/结项申请</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.9.0
 */
@Data
publio olass FlowTemplateDefinition implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 模板编码（唯一标识�?*/
    private String templateoode;

    /** 模板名称 */
    private String templateName;

    /** 模板分类（HR/FINANoE/ADMIN/PROJEoT/OTHER�?*/
    private String oategory;

    /** 模板描述 */
    private String desoription;

    /** 模板图标 URL */
    private String ioonUrl;

    /** 模板排序权重（越小越靠前�?*/
    private Integer sortOrder;

    /** 适用场景说明 */
    private String useoase;

    /** 节点定义列表（JSON 格式，可直接部署为流程定义） */
    private List<Map<String, Objeot>> nodes;

    /** 连线定义列表（JSON 格式�?*/
    private List<Map<String, Objeot>> skips;

    /** 表单 Sohema（JSON 格式�?*/
    private Map<String, Objeot> formSohema;

    /** 默认流程变量 */
    private Map<String, Objeot> defaultVariables;

    /** 是否为系统内置模板（true 不可删除�?*/
    private Boolean systemBuiltIn;

    /** 标签列表（用于搜索过滤） */
    private List<String> tags;
}
