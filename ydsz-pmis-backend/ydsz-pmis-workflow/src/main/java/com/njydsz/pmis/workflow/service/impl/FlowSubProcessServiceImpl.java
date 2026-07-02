package com.njydsz.pmis.workflow.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.WorkflowFacade;
import com.njydsz.pmis.workflow.dto.FlowStartProcessDTO;
import com.njydsz.pmis.workflow.engine.FlowAdvancer;
import com.njydsz.pmis.workflow.engine.JsonHelper;
import com.njydsz.pmis.workflow.entity.FlowDefinitionDO;
import com.njydsz.pmis.workflow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.entity.FlowNodeDO;
import com.njydsz.pmis.workflow.enums.FlowInstanceStatus;
import com.njydsz.pmis.workflow.mapper.FlowDefinitionMapper;
import com.njydsz.pmis.workflow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.service.FlowDefinitionService;
import com.njydsz.pmis.workflow.service.FlowInstanceService;
import com.njydsz.pmis.workflow.service.FlowSubProcessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流子流程服务实现
 *
 * <p>P1-3: 通过 WorkflowFacade 启动子流程实例，
 * 通过事件回调推进父流程。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowSubProcessServiceImpl implements FlowSubProcessService {

    private final FlowInstanceMapper instanceMapper;
    private final FlowDefinitionMapper definitionMapper;
    private final FlowDefinitionService definitionService;
    private final FlowInstanceService instanceService;
    private final FlowAdvancer advancer;
    private final WorkflowFacade workflowFacade;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long startSubProcess(FlowInstanceDO parentInstance,
                                FlowNodeDO callActivityNode,
                                Map<String, Object> variables) {
        if (parentInstance == null || callActivityNode == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "父实例/callActivity 节点不能为空");
        }
        // 1. 从节点 ext JSON 提取子流程编码
        String subFlowCode = extractSubFlowCode(callActivityNode);
        if (subFlowCode == null || subFlowCode.isBlank()) {
            throw new BizException(BizErrorCode.BAD_REQUEST,
                    "callActivity 节点未配置子流程编码: nodeCode=" + callActivityNode.getNodeCode());
        }
        // 2. 校验子流程定义存在且已发布
        FlowDefinitionDO subDef = definitionService.getPublished(subFlowCode, null,
                parentInstance.getTenantId());
        if (subDef == null) {
            throw new BizException(BizErrorCode.NOT_FOUND,
                    "子流程定义未发布或不存在: flowCode=" + subFlowCode);
        }
        // 3. 通过 WorkflowFacade 启动子流程（parentInstanceId 由 DTO 传递）
        FlowStartProcessDTO dto = buildSubProcessStartDTO(parentInstance, subFlowCode, variables);
        dto.setParentInstanceId(parentInstance.getId());
        dto.setParentNodeCode(callActivityNode.getNodeCode());
        String childIdStr = workflowFacade.startProcess(dto);
        Long childId = childIdStr == null ? null : Long.parseLong(childIdStr);
        log.info("[SubProcess] 启动子流程: parentInstance={} callActivityNode={} childInstance={} subFlowCode={}",
                parentInstance.getId(), callActivityNode.getNodeCode(), childId, subFlowCode);
        return childId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onSubProcessCompleted(Long childInstanceId) {
        if (childInstanceId == null) {
            return;
        }
        FlowInstanceDO child = instanceMapper.selectById(childInstanceId);
        if (child == null) {
            log.warn("[SubProcess] 子实例不存在: id={}", childInstanceId);
            return;
        }
        Long parentId = child.getParentInstanceId();
        String parentNodeCode = child.getParentNodeCode();
        if (parentId == null || parentNodeCode == null) {
            // 非子流程场景
            return;
        }
        FlowInstanceDO parent = instanceMapper.selectById(parentId);
        if (parent == null) {
            log.warn("[SubProcess] 父实例不存在: id={}", parentId);
            return;
        }
        if (!"RUNNING".equalsIgnoreCase(parent.getFlowStatus())) {
            log.info("[SubProcess] 父实例非运行态，跳过回调: id={} status={}",
                    parentId, parent.getFlowStatus());
            return;
        }
        // 推进父流程到 callActivity 节点的下一节点
        Map<String, Object> variables = parseVariables(parent.getVariable());
        List<FlowNodeDO> nextNodes = advancer.advance(parent, parentNodeCode,
                "PASS", null, variables);
        if (nextNodes.isEmpty()) {
            // 父流程无下一节点：完成
            instanceService.complete(parent.getId(), parentNodeCode);
            log.info("[SubProcess] 子流程完成触发父流程结束: parent={} child={}",
                    parentId, childInstanceId);
            return;
        }
        ((FlowInstanceServiceImpl) instanceService).generateTasksForNodes(
                parent.getId(), nextNodes, variables);
        FlowNodeDO first = nextNodes.get(0);
        instanceMapper.updateStatus(parent.getId(), parent.getFlowStatus(),
                first.getNodeCode(), first.getNodeName(), null, null);
        log.info("[SubProcess] 子流程完成触发父流程推进: parent={} → next={}",
                parentId, first.getNodeCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onSubProcessTerminated(Long childInstanceId, String reason, boolean terminal) {
        if (childInstanceId == null) {
            return;
        }
        FlowInstanceDO child = instanceMapper.selectById(childInstanceId);
        if (child == null) {
            return;
        }
        Long parentId = child.getParentInstanceId();
        String parentNodeCode = child.getParentNodeCode();
        if (parentId == null || parentNodeCode == null) {
            return;
        }
        FlowInstanceDO parent = instanceMapper.selectById(parentId);
        if (parent == null) {
            return;
        }
        if (terminal) {
            instanceService.terminate(parent.getId(), reason);
            log.info("[SubProcess] 子流程终止触发父流程终止: parent={} child={} reason={}",
                    parentId, childInstanceId, reason);
        } else {
            // 驳回：父流程状态置为 REJECTED
            instanceMapper.updateStatus(parent.getId(),
                    FlowInstanceStatus.REJECTED.name(), null, null,
                    java.time.LocalDateTime.now(), null);
            log.info("[SubProcess] 子流程驳回触发父流程驳回: parent={} child={} reason={}",
                    parentId, childInstanceId, reason);
        }
    }

    @Override
    public List<FlowInstanceDO> listChildren(Long parentInstanceId) {
        if (parentInstanceId == null) {
            return List.of();
        }
        return instanceMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<FlowInstanceDO>()
                .eq("parent_instance_id", parentInstanceId)
                .eq("deleted", 0)
                .orderByDesc("start_at"));
    }

    @Override
    public FlowStartProcessDTO buildSubProcessStartDTO(FlowInstanceDO parentInstance,
                                                       String subFlowCode,
                                                       Map<String, Object> variables) {
        FlowStartProcessDTO dto = new FlowStartProcessDTO();
        dto.setFlowCode(subFlowCode);
        dto.setTitle(parentInstance.getTitle() == null
                ? "子流程-" + subFlowCode
                : "[子流程] " + parentInstance.getTitle());
        dto.setBusinessType("SUB_" + parentInstance.getBusinessType());
        dto.setBusinessId(parentInstance.getBusinessId());
        dto.setBusinessNo(parentInstance.getBusinessNo());
        dto.setInitiatorId(parentInstance.getInitiatorId());
        dto.setInitiatorName(parentInstance.getInitiatorName());
        dto.setTenantId(parentInstance.getTenantId());
        dto.setVariables(variables == null ? new HashMap<>() : new HashMap<>(variables));
        dto.setProviderTraceId(parentInstance.getProviderTraceId());
        return dto;
    }

    // ============== 私有 ==============

    private String extractSubFlowCode(FlowNodeDO node) {
        if (node.getExt() == null || node.getExt().isBlank()) {
            return null;
        }
        try {
            Map<String, Object> ext = JsonHelper.fromJson(node.getExt());
            if (ext == null) return null;
            Object v = ext.get("callActivityFlowCode");
            if (v == null) {
                v = ext.get("subProcessFlowCode");
            }
            return v == null ? null : v.toString();
        } catch (Exception e) {
            log.warn("[SubProcess] 节点 ext 解析失败: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseVariables(String variableJson) {
        if (variableJson == null || variableJson.isBlank()) {
            return new HashMap<>();
        }
        try {
            Map<String, Object> map = com.alibaba.fastjson2.JSON.parseObject(variableJson, Map.class);
            return map == null ? new HashMap<>() : map;
        } catch (Exception e) {
            return new HashMap<>();
        }
    }
}
