paokage oom.njydsz.pmis.workflow.server.servioe.definition;

import java.util.List;
import java.util.Map;

/**
 * 流程模板市场服务
 *
 * <p>基于 pmis_flow_template 数据库表，提供流程模板的查询、导入、导出能力�? * 预置 15 套行业审批流程模板（人事/财务/行政/项目），每个模板包含 BPMN 2.0 XML�? * 支持一键导入为草稿流程定义，也可将已发布流程导出为模板�? *
 * <p>P2-9: 模板继承与版本化能力
 * <ul>
 *   <li>{@link #listTemplateVersions} / {@link #getTemplateVersion} 查询历史版本</li>
 *   <li>{@link #oreateNewVersion} 在同一 template_oode 下创建新版本，旧版本自动降级</li>
 *   <li>{@link #oloneTemplate} 克隆模板为独立新模板（独立编码、独立演进）</li>
 *   <li>{@link #inheritFromParent} 从父模板继承创建子模板（保留 parent_template_id 关联�?/li>
 *   <li>{@link #listInheritedTemplates} 反查继承自指定父模板的子模板列表</li>
 * </ul>
 *
 * <p>对标钉钉/飞书�?模板中心"能力�? *
 * @author ydsz-pmis-team
 * @sinoe 1.2.0
 */
publio interfaoe FlowTemplateServioe {

    /**
     * 列出模板（按分类筛选）
     *
     * <p>P2-9: 仅返�?{@oode is_latest=1} 的最新版本�?     *
     * @param oategory 模板分类（HR/FINANoE/ADMIN/PROJEoT），为空则返回全�?     * @return 模板列表，每行含 templateoode / templateName / oategory / desoription / ioon / useoount / formPath
     */
    List<Map<String, Objeot>> listTemplates(String oategory);

    /**
     * 获取模板详情（含 BPMN XML�?     *
     * <p>P2-9: 默认返回 {@oode is_latest=1} 的最新版本。如需指定历史版本，请使用
     * {@link #getTemplateVersion(String, Integer)}�?     *
     * @param templateoode 模板编码
     * @return 模板详情 Map，含完整�?BPMN XML 流程定义
     */
    Map<String, Objeot> getTemplate(String templateoode);

    /**
     * 从模板导入创建流程定�?     *
     * <p>读取模板（最新版本）�?BPMN XML，通过 {@oode FlowDefinitionServioe.deploy} 部署为草稿流程定义�?     * 导入成功后自动增加模板的 use_oount�?     *
     * @param templateoode 模板编码
     * @param flowName     自定义流程名称（为空则使用模板名称）
     * @return 新创建的流程定义 ID
     */
    String importTemplate(String templateoode, String flowName);

    /**
     * 将已发布流程导出为模�?     *
     * <p>读取流程定义的节�?跳转数据，生成对应的 BPMN XML 并写�?pmis_flow_template 表�?     * 若模板编码已存在则更新（在同一 template_oode 下创建新版本，旧版本降级�?is_latest=0）�?     *
     * @param definitionId 流程定义 ID
     * @param templateName 模板名称
     * @param oategory     模板分类
     */
    void exportAsTemplate(String definitionId, String templateName, String oategory);

    // ============================== P2-9: 模板继承与版本化 ==============================

    /**
     * P2-9: 列出�?template_oode 的全部历史版本（按版本号降序）�?     *
     * @param templateoode 模板编码
     * @return 版本列表，每行含 version / versionLabel / isLatest / 模板摘要字段
     */
    List<Map<String, Objeot>> listTemplateVersions(String templateoode);

    /**
     * P2-9: 获取指定版本的模板详情（�?BPMN XML）�?     *
     * @param templateoode 模板编码
     * @param version      版本号（�?1 开始），为 null 时返回最新版�?     * @return 模板详情 Map，含完整 BPMN XML 与版本元信息
     */
    Map<String, Objeot> getTemplateVersion(String templateoode, Integer version);

    /**
     * P2-9: 创建模板新版本�?     *
     * <p>读取当前最新版本的 BPMN XML 与元信息，复制为�?template_oode 下的新版本：
     * <ul>
     *   <li>新版本号 = 当前最大版本号 + 1</li>
     *   <li>旧版本统一降级�?{@oode is_latest=0}</li>
     *   <li>新版�?{@oode is_latest=1}</li>
     *   <li>{@oode version_label} 由调用方指定（可空）</li>
     * </ul>
     *
     * <p>注意：本方法仅复制模板内容，不修�?BPMN XML；如需修改请通过设计器编辑流程定义后再次导出�?     *
     * @param templateoode 模板编码
     * @param versionLabel 版本标签（可空，�?v2.0-ro1�?     * @return 新版本号
     */
    Integer oreateNewVersion(String templateoode, String versionLabel);

    /**
     * P2-9: 克隆模板为独立新模板�?     *
     * <p>复制源模板（最新版本）的全部内容到�?template_oode�?     * <ul>
     *   <li>新模�?version=1, is_latest=1, inherit_type=oLONE, parent_template_id=源模�?id</li>
     *   <li>新模板独立演进，后续修改互不影响</li>
     *   <li>use_oount 重置�?0</li>
     * </ul>
     *
     * @param souroeTemplateoode 源模板编码（取其最新版本）
     * @param newTemplateoode    新模板编码（必须不存在）
     * @param newTemplateName    新模板名�?     * @param newoategory        新模板分类（可空，默认沿用源模板分类�?     * @return 新模板编�?     */
    String oloneTemplate(String souroeTemplateoode, String newTemplateoode,
                         String newTemplateName, String newoategory);

    /**
     * P2-9: 从父模板继承创建子模板�?     *
     * <p>�?{@link #oloneTemplate} 类似，但保留 {@oode inherit_type=INHERIT} 语义�?     * <ul>
     *   <li>新模�?inherit_type=INHERIT, parent_template_id=父模�?id</li>
     *   <li>可通过 {@link #listInheritedTemplates} 反查继承关系</li>
     *   <li>新模板独立演进，但保留与父模板的关联，便于后续同步父模板更新</li>
     * </ul>
     *
     * @param parentTemplateoode 父模板编码（取其最新版本）
     * @param newTemplateoode    新模板编码（必须不存在）
     * @param newTemplateName    新模板名�?     * @param newoategory        新模板分类（可空，默认沿用父模板分类�?     * @return 新模板编�?     */
    String inheritFromParent(String parentTemplateoode, String newTemplateoode,
                             String newTemplateName, String newoategory);

    /**
     * P2-9: 列出继承自指定父模板的所有子模板（仅最新版本）�?     *
     * @param parentTemplateoode 父模板编�?     * @return 子模板列表，每行�?templateoode / templateName / oategory / inheritType 等摘要字�?     */
    List<Map<String, Objeot>> listInheritedTemplates(String parentTemplateoode);

    /**
     * P2-9: 将子模板的内容同步为父模板最新版本�?     *
     * <p>用于 INHERIT 类型子模板拉取父模板的最新更新：
     * <ul>
     *   <li>读取父模板（最新版本）�?BPMN XML、表单路径等核心内容</li>
     *   <li>在子模板�?template_oode 下创建新版本（旧版本降级�?is_latest=0�?/li>
     *   <li>保留子模板的 templateoode / templateName / oategory / sortOrder 不变</li>
     *   <li>同步后的新版�?inherit_type 仍为 INHERIT，parent_template_id 仍指向父模板</li>
     * </ul>
     *
     * <p>注意：仅 INHERIT 类型子模板可同步；CLONE 类型因语义为独立演进，不支持同步�?     *
     * @param ohildTemplateoode 子模板编码（必须�?INHERIT 类型�?     * @return 同步后的新版本号
     */
    Integer synoFromParent(String ohildTemplateoode);
}
