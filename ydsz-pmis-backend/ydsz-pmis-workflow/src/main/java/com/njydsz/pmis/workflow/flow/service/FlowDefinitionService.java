package com.njydsz.pmis.workflow.flow.service;

import com.njydsz.pmis.workflow.flow.dto.FlowDeployProcessDTO;
import com.njydsz.pmis.workflow.flow.entity.FlowDefinitionDO;

import java.util.List;
import java.util.Map;

/**
 * 流程定义 Service
 *
 * <p>提供流程部署、发布、停用、查询等能力，是工作流引擎的入口服务。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface FlowDefinitionService {

    /**
     * 部署流程（基于 JSON 模型）
     *
     * @return 流程定义 ID
     */
    Long deploy(FlowDeployProcessDTO dto);

    /**
     * 发布流程
     */
    void publish(Long definitionId);

    /**
     * 停用（失效）流程
     */
    void deprecate(Long definitionId);

    /**
     * 查最新已发布版本
     */
    FlowDefinitionDO getPublished(String flowCode, String version, Long tenantId);

    /**
     * 按编码查最新
     */
    FlowDefinitionDO getLatestByCode(String flowCode, Long tenantId);

    /**
     * 分页查询
     */
    List<FlowDefinitionDO> page(int pageNo, int pageSize, String category, String flowCode);

    /**
     * P2-21: 流程定义详情查询（含节点 + 跳转）
     *
     * @param definitionId 流程定义 ID
     * @return Map 包含 definition / nodes / skips 三个 key；定义不存在返回 null
     */
    Map<String, Object> getDetail(Long definitionId);
}
