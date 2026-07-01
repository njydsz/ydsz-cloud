package com.njydsz.pmis.workflow.flow.service;

import com.njydsz.pmis.workflow.flow.dto.FlowDeployProcessDTO;
import com.njydsz.pmis.workflow.flow.entity.FlowDefinitionDO;

import java.util.List;

/**
 * 流程定义 Service
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
}
