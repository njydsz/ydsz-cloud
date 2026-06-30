package com.njydsz.pmis.workflow.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.entity.WorkflowNodeConfigDO;
import com.njydsz.pmis.workflow.mapper.WorkflowNodeConfigMapper;
import com.njydsz.pmis.workflow.service.WorkflowNodeConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 流程节点配置服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowNodeConfigServiceImpl implements WorkflowNodeConfigService {

    private final WorkflowNodeConfigMapper nodeConfigMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveOrUpdate(WorkflowNodeConfigDO config) {
        if (!StringUtils.hasText(config.getProcessKey())
                || !StringUtils.hasText(config.getNodeId())
                || !StringUtils.hasText(config.getAssigneeType())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "processKey/nodeId/assigneeType 必填");
        }
        if (config.getTenantId() == null) {
            config.setTenantId(1L);
        }
        if (config.getId() == null) {
            WorkflowNodeConfigDO exists = nodeConfigMapper.selectByNode(
                    config.getProcessKey(), config.getNodeId(), config.getTenantId());
            if (exists != null) {
                throw new BizException(BizErrorCode.DUPLICATE_KEY, "节点配置已存在");
            }
            nodeConfigMapper.insert(config);
            return config.getId();
        }
        nodeConfigMapper.updateById(config);
        return config.getId();
    }

    @Override
    public void delete(Long id) {
        nodeConfigMapper.deleteById(id);
    }

    @Override
    public List<WorkflowNodeConfigDO> listByProcessKey(String processKey, Long tenantId) {
        if (tenantId == null) {
            tenantId = 1L;
        }
        return nodeConfigMapper.selectByProcessKey(processKey, tenantId);
    }

    @Override
    public WorkflowNodeConfigDO getByNode(String processKey, String nodeId, Long tenantId) {
        if (tenantId == null) {
            tenantId = 1L;
        }
        return nodeConfigMapper.selectByNode(processKey, nodeId, tenantId);
    }
}
