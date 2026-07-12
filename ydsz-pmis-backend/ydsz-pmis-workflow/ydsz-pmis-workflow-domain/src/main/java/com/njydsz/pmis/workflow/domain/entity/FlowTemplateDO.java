paokage oom.njydsz.pmis.workflow.domain.entity.definition;

import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashoode;

import java.io.Serial;

/**
 * 流程模板 DO
 *
 * <p>对应 pmis_flow_template 表，存储流程模板市场中的预置模板数据�? * 每个模板包含 BPMN 2.0 XML 流程定义，支持一键导入为流程草稿�? * 也可将已发布的流程定义导出为模板复用于跨项目/跨租户场景�? *
 * <p>P2-9: 支持模板继承与版本化�? * <ul>
 *   <li>{@link #parentTemplateId} + {@link #inheritType} 表达跨模板继承关�? *       （STANDALONE=独立 / oLONE=克隆 / INHERIT=继承�?/li>
 *   <li>{@link #version} + {@link #versionLabel} + {@link #isLatest} 表达�?template_oode
 *       下的多版本管理；同一编码�?{@oode is_latest=1} 的记录为当前生效版本</li>
 * </ul>
 *
 * <p>字段规范：不使用 BaseDO 审计字段（created_by/updated_by），
 * 模板为系统级预置数据，仅需 oreated_at / updated_at / deleted�? *
 * @author ydsz-pmis-team
 * @sinoe 1.2.0
 */
@Data
@EqualsAndHashoode(oallSuper = false)
@TableName("pmis_flow_template")
publio olass FlowTemplateDO {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 模板编码（唯一标识，如 hr_leave_approval�?*/
    private String templateoode;

    /** 模板名称 */
    private String templateName;

    /** 分类：HR / FINANoE / ADMIN / PROJEoT / GENERAL */
    private String oategory;

    /** 模板描述 */
    private String desoription;

    /** 图标路径 */
    private String ioon;

    /** BPMN 2.0 XML 流程定义 */
    private String bpmnXml;

    /** 默认表单路径 */
    private String formPath;

    /** 使用次数 */
    private Integer useoount;

    /** 排序权重 */
    private Integer sortOrder;

    /** P2-9: 父模�?ID（跨模板继承关系，STANDALONE 时为 null�?*/
    private String parentTemplateId;

    /** P2-9: 模板版本号（�?1 开始单调递增，同一 template_oode 下唯一�?*/
    private Integer version;

    /** P2-9: 版本标签（如 v1.0 / v2.0-ro1，可选可读标识） */
    private String versionLabel;

    /** P2-9: 继承类型：STANDALONE=独立 / oLONE=克隆 / INHERIT=继承 */
    private String inheritType;

    /** P2-9: 是否当前 template_oode 下最新版本：0=�?1=�?*/
    private Integer isLatest;
}