package com.njydsz.pmis.workflow.flow.service;

import java.util.List;
import java.util.Map;

/**
 * GAP-P1: 流程模板服务
 *
 * <p>预置 PMIS 领域常见审批流程模板（采购审批、费用报销、出差申请、用印申请等），
 * 支持一键导入为草稿流程定义，降低用户从零搭建流程的门槛。
 *
 * <p>对标钉钉/飞书的"模板中心"能力。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
public interface FlowTemplateService {

    /**
     * 列出所有可用模板
     *
     * @param category 模板分类（可空，为空则返回全部分类）
     * @return 模板列表，每行含 code / name / category / description
     */
    List<Map<String, Object>> listTemplates(String category);

    /**
     * 一键导入模板（部署为草稿）
     *
     * @param templateCode 模板编码
     * @param tenantId     租户 ID
     * @return 新创建的流程定义 ID
     */
    Long importTemplate(String templateCode, Long tenantId);

    /**
     * 预览模板内容
     *
     * @param templateCode 模板编码
     * @return 模板详情 Map，含 code / name / category / description / nodes / skips
     */
    Map<String, Object> previewTemplate(String templateCode);
}
