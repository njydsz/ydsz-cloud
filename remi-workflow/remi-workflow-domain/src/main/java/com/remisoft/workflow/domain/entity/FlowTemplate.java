package com.remisoft.workflow.domain.entity;

import java.io.Serial;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import com.remisoft.common.jdbc.entity.MpBaseEntity;

/**
 * 流程模板实体
 *
 * <p>对应数据库表 {@code remi_flow_template}，存储流程模板市场中的预置模板数据。
 * 每个模板包含 BPMN 2.0 XML 流程定义，支持一键导入为流程草稿，
 * 也可将已发布的流程定义导出为模板复用于跨项目/跨租户场景。
 *
 * <p><b>核心使用场景：</b>
 * <ul>
 *   <li>新租户/新项目接入时，从模板市场快速创建标准化流程</li>
 *   <li>管理员将已稳定运行的流程定义导出为模板，作为最佳实践沉淀</li>
 *   <li>模板市场页面按分类展示，供业务方浏览选用</li>
 * </ul>
 *
 * <p><b>P2-9: 模板继承与版本化：</b>
 * <ul>
 *   <li>{@link #parentTemplateId} + {@link #inheritType} 表达跨模板继承关系
 *       （{@code STANDALONE}=独立 / {@code CLONE}=克隆 / {@code INHERIT}=继承）</li>
 *   <li>{@link #version} + {@link #versionLabel} + {@link #isLatest} 表达同 {@code templateCode} 下的多版本管理；
 *       同一编码下 {@code isLatest=1} 的记录为当前生效版本</li>
 * </ul>
 *
 * <p><b>字段规范：</b>使用 {@link MpBaseEntity} 继承审计字段（{@code createdBy/updatedBy/createdAt/updatedAt}），
 * 模板的 {@code createdBy} 默认填充为 {@code SYSTEM}，由系统初始化。
 *
 * <p><b>索引设计：</b>
 * <ul>
 *   <li>唯一索引 {@code uk_template_code_version}（{@code template_code}, {@code version}）</li>
 *   <li>普通索引 {@code idx_category}（{@code category}）：按分类筛选</li>
 *   <li>普通索引 {@code idx_parent}（{@code parent_template_id}）：模板继承关系</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 *
 * @see FlowDefinition 流程定义
 * @see com.remisoft.workflow.server.service.FlowTemplateService 模板服务
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("remi_flow_template")
public class FlowTemplate extends MpBaseEntity<String> {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 模板编码（唯一标识，如 {@code hr_leave_approval}） */
    private String templateCode;

    /** 模板名称（前端展示） */
    private String templateName;

    /** 分类：{@code HR} / {@code FINANCE} / {@code ADMIN} / {@code PROJECT} / {@code GENERAL} */
    private String category;

    /** 模板描述（说明适用场景、必填字段、注意事项） */
    private String description;

    /** 图标路径（前端展示用） */
    private String icon;

    /** BPMN 2.0 XML 流程定义（{@code <bpmn:definitions>...</bpmn:definitions>}） */
    private String bpmnXml;

    /** 默认表单路径（导入后默认关联的审批表单） */
    private String formPath;

    /** 使用次数（被导入到流程定义的累计计数，用于热门度排序） */
    private Integer useCount;

    /** 排序权重（越大越靠前，模板市场首页展示用） */
    private Integer sortOrder;

    /** 父模板 ID（跨模板继承关系，{@code STANDALONE} 时为 {@code null}） */
    private String parentTemplateId;

    /** 模板版本号（从 {@code 1} 开始单调递增，同一 {@code templateCode} 下唯一） */
    private Integer version;

    /** 版本标签（如 {@code v1.0} / {@code v2.0-rc1}，可选可读标识） */
    private String versionLabel;

    /** 继承类型：{@code STANDALONE}=独立 / {@code CLONE}=克隆 / {@code INHERIT}=继承 */
    private String inheritType;

    /** 是否当前 {@code templateCode} 下最新版本：{@code 0}=否 / {@code 1}=是 */
    private Integer isLatest;
}
