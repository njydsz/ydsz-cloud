package com.njydsz.pmis.workflow.service;

import com.njydsz.pmis.workflow.entity.WorkflowNodeConfigDO;

import java.util.List;

/**
 * 流程节点配置服务
 */
public interface WorkflowNodeConfigService {

    /**
     * 新增/更新节点配置
     */
    Long saveOrUpdate(WorkflowNodeConfigDO config);

    /**
     * 删除节点配置
     */
    void delete(Long id);

    /**
     * 查询流程的所有节点配置
     */
    List<WorkflowNodeConfigDO> listByProcessKey(String processKey, Long tenantId);

    /**
     * 查询单个节点配置
     */
    WorkflowNodeConfigDO getByNode(String processKey, String nodeId, Long tenantId);
}
