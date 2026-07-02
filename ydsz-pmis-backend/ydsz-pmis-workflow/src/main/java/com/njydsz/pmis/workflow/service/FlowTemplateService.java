package com.njydsz.pmis.workflow.service;

import java.util.List;
import java.util.Map;

/**
 * 流程模板市场服务
 *
 * <p>基于 pmis_flow_template 数据库表，提供流程模板的查询、导入、导出能力。
 * 预置 15 套行业审批流程模板（人事/财务/行政/项目），每个模板包含 BPMN 2.0 XML，
 * 支持一键导入为草稿流程定义，也可将已发布流程导出为模板。
 *
 * <p>对标钉钉/飞书的"模板中心"能力。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
public interface FlowTemplateService {

    /**
     * 列出模板（按分类筛选）
     *
     * @param category 模板分类（HR/FINANCE/ADMIN/PROJECT），为空则返回全部
     * @return 模板列表，每行含 templateCode / templateName / category / description / icon / useCount / formPath
     */
    List<Map<String, Object>> listTemplates(String category);

    /**
     * 获取模板详情（含 BPMN XML）
     *
     * @param templateCode 模板编码
     * @return 模板详情 Map，含完整的 BPMN XML 流程定义
     */
    Map<String, Object> getTemplate(String templateCode);

    /**
     * 从模板导入创建流程定义
     *
     * <p>读取模板的 BPMN XML，通过 {@code FlowDefinitionService.deploy} 部署为草稿流程定义。
     * 导入成功后自动增加模板的 use_count。
     *
     * @param templateCode 模板编码
     * @param flowName     自定义流程名称（为空则使用模板名称）
     * @return 新创建的流程定义 ID
     */
    Long importTemplate(String templateCode, String flowName);

    /**
     * 将已发布流程导出为模板
     *
     * <p>读取流程定义的节点/跳转数据，生成对应的 BPMN XML 并写入 pmis_flow_template 表。
     * 若模板编码已存在则更新。
     *
     * @param definitionId 流程定义 ID
     * @param templateName 模板名称
     * @param category     模板分类
     */
    void exportAsTemplate(Long definitionId, String templateName, String category);
}