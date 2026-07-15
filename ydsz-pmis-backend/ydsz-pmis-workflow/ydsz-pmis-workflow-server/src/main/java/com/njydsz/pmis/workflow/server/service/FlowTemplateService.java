package com.njydsz.pmis.workflow.server.service;

import java.util.List;
import java.util.Map;

/**
 * 流程模板市场服务
 *
 * <p>基于 pmis_flow_template 数据库表，提供流程模板的查询、导入、导出能力。
 * 预置 15 套行业审批流程模板（人事/财务/行政/项目），每个模板包含 BPMN 2.0 XML，
 * 支持一键导入为草稿流程定义，也可将已发布流程导出为模板。
 *
 * <p>P2-9: 模板继承与版本化能力
 * <ul>
 *   <li>{@link #listTemplateVersions} / {@link #getTemplateVersion} 查询历史版本</li>
 *   <li>{@link #createNewVersion} 在同一 template_code 下创建新版本，旧版本自动降级</li>
 *   <li>{@link #cloneTemplate} 克隆模板为独立新模板（独立编码、独立演进）</li>
 *   <li>{@link #inheritFromParent} 从父模板继承创建子模板（保留 parent_template_id 关联）</li>
 *   <li>{@link #listInheritedTemplates} 反查继承自指定父模板的子模板列表</li>
 * </ul>
 *
 * <p>对标钉钉/飞书的"模板中心"能力。
 *
 * @since 1.2.0
 */
public interface FlowTemplateService {

    /**
     * 列出模板（按分类筛选）
     *
     * <p>P2-9: 仅返回 {@code is_latest=1} 的最新版本。
     *
     * @param category 模板分类（HR/FINANCE/ADMIN/PROJECT），为空则返回全部
     * @return 模板列表，每行含 templateCode / templateName / category / description / icon / useCount / formPath
     */
    List<Map<String, Object>> listTemplates(String category);

    /**
     * 获取模板详情（含 BPMN XML）
     *
     * <p>P2-9: 默认返回 {@code is_latest=1} 的最新版本。如需指定历史版本，请使用
     * {@link #getTemplateVersion(String, Integer)}。
     *
     * @param templateCode 模板编码
     * @return 模板详情 Map，含完整的 BPMN XML 流程定义
     */
    Map<String, Object> getTemplate(String templateCode);

    /**
     * 从模板导入创建流程定义
     *
     * <p>读取模板（最新版本）的 BPMN XML，通过 {@code FlowDefinitionService.deploy} 部署为草稿流程定义。
     * 导入成功后自动增加模板的 use_count。
     *
     * @param templateCode 模板编码
     * @param flowName     自定义流程名称（为空则使用模板名称）
     * @return 新创建的流程定义 ID
     */
    String importTemplate(String templateCode, String flowName);

    /**
     * 将已发布流程导出为模板
     *
     * <p>读取流程定义的节点/跳转数据，生成对应的 BPMN XML 并写入 pmis_flow_template 表。
     * 若模板编码已存在则更新（在同一 template_code 下创建新版本，旧版本降级为 is_latest=0）。
     *
     * @param definitionId 流程定义 ID
     * @param templateName 模板名称
     * @param category     模板分类
     */
    void exportAsTemplate(String definitionId, String templateName, String category);

    // ============================== P2-9: 模板继承与版本化 ==============================

    /**
     * P2-9: 列出某 template_code 的全部历史版本（按版本号降序）。
     *
     * @param templateCode 模板编码
     * @return 版本列表，每行含 version / versionLabel / isLatest / 模板摘要字段
     */
    List<Map<String, Object>> listTemplateVersions(String templateCode);

    /**
     * P2-9: 获取指定版本的模板详情（含 BPMN XML）。
     *
     * @param templateCode 模板编码
     * @param version      版本号（从 1 开始），为 null 时返回最新版本
     * @return 模板详情 Map，含完整 BPMN XML 与版本元信息
     */
    Map<String, Object> getTemplateVersion(String templateCode, Integer version);

    /**
     * P2-9: 创建模板新版本。
     *
     * <p>读取当前最新版本的 BPMN XML 与元信息，复制为同 template_code 下的新版本：
     * <ul>
     *   <li>新版本号 = 当前最大版本号 + 1</li>
     *   <li>旧版本统一降级为 {@code is_latest=0}</li>
     *   <li>新版本 {@code is_latest=1}</li>
     *   <li>{@code version_label} 由调用方指定（可空）</li>
     * </ul>
     *
     * <p>注意：本方法仅复制模板内容，不修改 BPMN XML；如需修改请通过设计器编辑流程定义后再次导出。
     *
     * @param templateCode 模板编码
     * @param versionLabel 版本标签（可空，如 v2.0-rc1）
     * @return 新版本号
     */
    Integer createNewVersion(String templateCode, String versionLabel);

    /**
     * P2-9: 克隆模板为独立新模板。
     *
     * <p>复制源模板（最新版本）的全部内容到新 template_code：
     * <ul>
     *   <li>新模板 version=1, is_latest=1, inherit_type=CLONE, parent_template_id=源模板 id</li>
     *   <li>新模板独立演进，后续修改互不影响</li>
     *   <li>use_count 重置为 0</li>
     * </ul>
     *
     * @param sourceTemplateCode 源模板编码（取其最新版本）
     * @param newTemplateCode    新模板编码（必须不存在）
     * @param newTemplateName    新模板名称
     * @param newCategory        新模板分类（可空，默认沿用源模板分类）
     * @return 新模板编码
     */
    String cloneTemplate(String sourceTemplateCode, String newTemplateCode,
                         String newTemplateName, String newCategory);

    /**
     * P2-9: 从父模板继承创建子模板。
     *
     * <p>与 {@link #cloneTemplate} 类似，但保留 {@code inherit_type=INHERIT} 语义：
     * <ul>
     *   <li>新模板 inherit_type=INHERIT, parent_template_id=父模板 id</li>
     *   <li>可通过 {@link #listInheritedTemplates} 反查继承关系</li>
     *   <li>新模板独立演进，但保留与父模板的关联，便于后续同步父模板更新</li>
     * </ul>
     *
     * @param parentTemplateCode 父模板编码（取其最新版本）
     * @param newTemplateCode    新模板编码（必须不存在）
     * @param newTemplateName    新模板名称
     * @param newCategory        新模板分类（可空，默认沿用父模板分类）
     * @return 新模板编码
     */
    String inheritFromParent(String parentTemplateCode, String newTemplateCode,
                             String newTemplateName, String newCategory);

    /**
     * P2-9: 列出继承自指定父模板的所有子模板（仅最新版本）。
     *
     * @param parentTemplateCode 父模板编码
     * @return 子模板列表，每行含 templateCode / templateName / category / inheritType 等摘要字段
     */
    List<Map<String, Object>> listInheritedTemplates(String parentTemplateCode);

    /**
     * P2-9: 将子模板的内容同步为父模板最新版本。
     *
     * <p>用于 INHERIT 类型子模板拉取父模板的最新更新：
     * <ul>
     *   <li>读取父模板（最新版本）的 BPMN XML、表单路径等核心内容</li>
     *   <li>在子模板的 template_code 下创建新版本（旧版本降级为 is_latest=0）</li>
     *   <li>保留子模板的 templateCode / templateName / category / sortOrder 不变</li>
     *   <li>同步后的新版本 inherit_type 仍为 INHERIT，parent_template_id 仍指向父模板</li>
     * </ul>
     *
     * <p>注意：仅 INHERIT 类型子模板可同步；CLONE 类型因语义为独立演进，不支持同步。
     *
     * @param childTemplateCode 子模板编码（必须为 INHERIT 类型）
     * @return 同步后的新版本号
     */
    Integer syncFromParent(String childTemplateCode);
}
