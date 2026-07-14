package com.njydsz.pmis.workflow.server.service.impl.instance;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.njydsz.pmis.common.core.response.BaseResultCode;
import com.njydsz.pmis.common.exception.custom.SysException;
import com.njydsz.pmis.common.json.YdszJson;
import com.njydsz.pmis.workflow.WorkflowFacade;
import com.njydsz.pmis.workflow.domain.dto.FlowStartProcessDTO;
import com.njydsz.pmis.workflow.domain.entity.FlowDefinitionDO;
import com.njydsz.pmis.workflow.domain.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.domain.entity.FlowNodeDO;
import com.njydsz.pmis.workflow.domain.enums.FlowInstanceStatus;
import com.njydsz.pmis.workflow.infra.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.server.engine.FlowAdvancer;
import com.njydsz.pmis.workflow.server.engine.FlowEventListener;
import com.njydsz.pmis.workflow.server.engine.FlowWorkflowEvent;
import com.njydsz.pmis.workflow.server.engine.JsonHelper;
import com.njydsz.pmis.workflow.server.service.FlowDefinitionService;
import com.njydsz.pmis.workflow.server.service.FlowInstanceService;
import com.njydsz.pmis.workflow.server.service.FlowSubProcessService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 工作流子流程服务实现
 *
 * <p>P1-3: 通过 WorkflowFacade 启动子流程实例，
 * 通过事件回调推进父流程。
 *
 * <p>增强功能（子流程增强）：
 * <ul>
 *   <li>子流程独立超时处理：从 ext JSON 读取 subProcessTimeout 设置 dueAt</li>
 *   <li>父子流程数据上下文传递：合并父流程变量传递给子流程，子流程完成时回写</li>
 *   <li>子流程实例追踪：递归查询子流程树</li>
 *   <li>子流程嵌套层级限制：最大深度可配置（workflow.subprocess.max-nesting-depth，默认 3 层）</li>
 *   <li>子流程事件通知：触发 onInstanceStart 和发布 FlowWorkflowEvent</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowSubProcessServiceImpl implements FlowSubProcessService {

    /**
     * P2-8: 最大子流程嵌套深度（可配置）。
     *
     * <p>通过 {@code workflow.subprocess.max-nesting-depth} 属性配置，默认 3 层。
     * 生产环境可根据业务复杂度调整，建议不超过 10 层（过深嵌套难以维护且影响性能）。
     */
    @Value("${workflow.subprocess.max-nesting-depth:3}")
    private int maxNestingDepth;

    /** 流程实例 Mapper，查询/更新父实例和子流程实例 */
    private final FlowInstanceMapper instanceMapper;
    /** 流程定义服务，解析子流程的流程定义 */
    private final FlowDefinitionService definitionService;
    /** 流程实例服务，启动子流程实例 */
    private final FlowInstanceService instanceService;
    /** 流程推进引擎，子流程完成后推进父流程 */
    private final FlowAdvancer advancer;
    /** 工作流门面，启动子流程实例的统一入口 */
    private final WorkflowFacade workflowFacade;
    /** 事件监听器列表（可能为 null） */
    private final List<FlowEventListener> eventListeners;
    /** Spring 事件发布器（可能为 null） */
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String startSubProcess(FlowInstanceDO parentInstance,
                                FlowNodeDO callActivityNode,
                                Map<String, Object> variables) {
        if (parentInstance == null || callActivityNode == null) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "父实例/callActivity 节点不能为空");
        }
        // 1. 从节点 ext JSON 提取子流程编码
        String subFlowCode = extractSubFlowCode(callActivityNode);
        if (subFlowCode == null || subFlowCode.isBlank()) {
            throw new SysException(BaseResultCode.BAD_REQUEST,
                    "callActivity 节点未配置子流程编码: nodeCode=" + callActivityNode.getNodeCode());
        }
        // 2. 校验子流程定义存在且已发布
        FlowDefinitionDO subDef = definitionService.getPublished(subFlowCode, null,
                parentInstance.getTenantId());
        if (subDef == null) {
            throw new SysException(BaseResultCode.NOT_FOUND,
                    "子流程定义未发布或不存在: flowCode=" + subFlowCode);
        }
        // 3. 检查嵌套深度（P2-8: 可配置，默认 3 层）
        int nestingDepth = getNestingDepth(parentInstance.getId());
        if (nestingDepth >= maxNestingDepth) {
            throw new SysException(BaseResultCode.BAD_REQUEST,
                    "error.workflow.msg_14aff96e",
                    maxNestingDepth, nestingDepth, parentInstance.getId());
        }
        log.info("[SubProcess] 嵌套深度检查: parentInstance={} depth={} max={}",
                parentInstance.getId(), nestingDepth, maxNestingDepth);
        // 4. 将父流程 variables 合并传递给子流程（增强上下文传递）
        Map<String, Object> parentVars = instanceService.getVariables(parentInstance.getId());
        Map<String, Object> mergedVars = new HashMap<>(parentVars);
        if (variables != null) {
            mergedVars.putAll(variables);
        }
        // 标记父流程信息
        mergedVars.put("_parentInstanceId", parentInstance.getId());
        mergedVars.put("_parentNodeCode", callActivityNode.getNodeCode());
        mergedVars.put("_parentFlowCode", parentInstance.getFlowCode());
        // 5. 通过 WorkflowFacade 启动子流程（parentInstanceId 由 DTO 传递）
        FlowStartProcessDTO dto = buildSubProcessStartDTO(parentInstance, subFlowCode, mergedVars);
        dto.setParentInstanceId(parentInstance.getId());
        dto.setParentNodeCode(callActivityNode.getNodeCode());
        String childId = workflowFacade.startProcess(dto);
        // 6. 从 ext JSON 读取 subProcessTimeout 并设置 dueAt
        Double subProcessTimeout = extractSubProcessTimeout(callActivityNode);
        if (subProcessTimeout != null && subProcessTimeout > 0 && childId != null) {
            LocalDateTime dueAt = LocalDateTime.now().plusHours((long) Math.ceil(subProcessTimeout));
            instanceService.setDueAt(childId, dueAt);
            log.info("[SubProcess] 子流程超时设置: childInstance={} timeoutHours={} dueAt={}",
                    childId, subProcessTimeout, dueAt);
        }
        // 7. 触发 onInstanceStart 事件
        fireInstanceStart(childId, mergedVars);
        log.info("[SubProcess] 启动子流程: parentInstance={} callActivityNode={} childInstance={} subFlowCode={} depth={}",
                parentInstance.getId(), callActivityNode.getNodeCode(), childId, subFlowCode, nestingDepth + 1);
        return childId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onSubProcessCompleted(String childInstanceId) {
        if (childInstanceId == null) {
            return;
        }
        FlowInstanceDO child = instanceMapper.selectById(childInstanceId);
        if (child == null) {
            log.warn("[SubProcess] 子实例不存在: id={}", childInstanceId);
            return;
        }
        String parentId = child.getParentInstanceId();
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
        // 清除子流程超时标记
        instanceService.setDueAt(childInstanceId, null);
        // 将子流程的输出变量合并回父流程 variables
        Map<String, Object> childVars = instanceService.getVariables(childInstanceId);
        if (childVars != null && !childVars.isEmpty()) {
            // 过滤掉内部标记变量，只合并业务变量
            Map<String, Object> businessVars = new HashMap<>(childVars);
            businessVars.remove("_parentInstanceId");
            businessVars.remove("_parentNodeCode");
            businessVars.remove("_parentFlowCode");
            instanceService.setVariables(parentId, businessVars);
            log.info("[SubProcess] 子流程变量回写父流程: childId={} parentId={} varKeys={}",
                    childInstanceId, parentId, businessVars.keySet());
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
            // 发布异步事件
            publishWorkflowEvent("SUBPROCESS_COMPLETED", childInstanceId, parentId);
            return;
        }
        ((FlowInstanceServiceImpl) instanceService).generateTasksForNodes(
                parent.getId(), nextNodes, variables);
        FlowNodeDO first = nextNodes.get(0);
        instanceMapper.updateStatus(parent.getId(), parent.getFlowStatus(),
                first.getNodeCode(), first.getNodeName(), null, null);
        log.info("[SubProcess] 子流程完成触发父流程推进: parent={} → next={}",
                parentId, first.getNodeCode());
        // 发布异步事件
        publishWorkflowEvent("SUBPROCESS_COMPLETED", childInstanceId, parentId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onSubProcessTerminated(String childInstanceId, String reason, boolean terminal) {
        if (childInstanceId == null) {
            return;
        }
        FlowInstanceDO child = instanceMapper.selectById(childInstanceId);
        if (child == null) {
            return;
        }
        String parentId = child.getParentInstanceId();
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
                    LocalDateTime.now(), null);
            log.info("[SubProcess] 子流程驳回触发父流程驳回: parent={} child={} reason={}",
                    parentId, childInstanceId, reason);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<FlowInstanceDO> listChildren(String parentInstanceId) {
        if (parentInstanceId == null) {
            return List.of();
        }
        return instanceMapper.selectList(new QueryWrapper<FlowInstanceDO>()
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

    // ============== 新增公开方法 ==============

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getSubProcessContext(String childInstanceId) {
        if (childInstanceId == null) {
            return new HashMap<>();
        }
        FlowInstanceDO child = instanceMapper.selectById(childInstanceId);
        if (child == null) {
            log.warn("[SubProcess] getSubProcessContext 子实例不存在: id={}", childInstanceId);
            return new HashMap<>();
        }
        // 子流程自身变量
        Map<String, Object> childVars = instanceService.getVariables(childInstanceId);
        Map<String, Object> context = new HashMap<>(childVars);
        // 父流程变量
        String parentId = child.getParentInstanceId();
        if (parentId != null) {
            Map<String, Object> parentVars = instanceService.getVariables(parentId);
            context.putAll(parentVars);
            context.put("_parentInstanceId", parentId);
            context.put("_parentNodeCode", child.getParentNodeCode());
        }
        // 添加实例元信息
        context.put("_childInstanceId", childInstanceId);
        context.put("_childFlowCode", child.getFlowCode());
        context.put("_childFlowName", child.getFlowName());
        log.info("[SubProcess] getSubProcessContext: childId={} parentId={} varCount={}",
                childInstanceId, parentId, context.size());
        return context;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listSubProcessTree(String parentInstanceId) {
        List<Map<String, Object>> tree = new ArrayList<>();
        if (parentInstanceId == null) {
            return tree;
        }
        List<FlowInstanceDO> children = listChildren(parentInstanceId);
        for (FlowInstanceDO child : children) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("instanceId", child.getId());
            node.put("instanceName", child.getTitle());
            node.put("flowCode", child.getFlowCode());
            node.put("status", child.getFlowStatus());
            // 递归查询子节点的子流程
            List<Map<String, Object>> subProcesses = listSubProcessTree(child.getId());
            node.put("subProcesses", subProcesses);
            node.put("startAt", child.getStartAt());
            node.put("endAt", child.getEndAt());
            tree.add(node);
        }
        return tree;
    }

    // ============== 私有方法 ==============

    /**
     * 从节点 ext JSON 提取子流程编码
     */
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

    /**
     * 从节点 ext JSON 提取子流程超时小时数
     */
    private Double extractSubProcessTimeout(FlowNodeDO node) {
        if (node.getExt() == null || node.getExt().isBlank()) {
            return null;
        }
        try {
            Map<String, Object> ext = JsonHelper.fromJson(node.getExt());
            if (ext == null) return null;
            Object v = ext.get("subProcessTimeout");
            if (v == null) {
                return null;
            }
            return v instanceof Number ? ((Number) v).doubleValue() : Double.parseDouble(v.toString());
        } catch (Exception e) {
            log.warn("[SubProcess] 解析 subProcessTimeout 失败: nodeCode={} err={}",
                    node.getNodeCode(), e.getMessage());
            return null;
        }
    }

    /**
     * 递归计算嵌套深度（从 parentInstanceId 向上追溯）
     *
     * <p>P2-8: 迭代上限基于可配置的 {@link #maxNestingDepth}，额外加 10 作为安全余量，
     * 防止数据异常（如循环引用）导致无限递归。
     *
     * @param parentInstanceId 当前父流程实例 ID
     * @return 已有嵌套深度（不含当前层级）
     */
    private int getNestingDepth(String parentInstanceId) {
        int depth = 0;
        String currentId = parentInstanceId;
        // 防止无限循环：上限 = 配置最大深度 + 10 安全余量
        int maxIterations = maxNestingDepth + 10;
        while (currentId != null && depth < maxIterations) {
            FlowInstanceDO instance = instanceMapper.selectById(currentId);
            if (instance == null) {
                break;
            }
            String nextParentId = instance.getParentInstanceId();
            if (nextParentId == null) {
                break;
            }
            depth++;
            currentId = nextParentId;
        }
        return depth;
    }

    /**
     * 触发 onInstanceStart 事件
     */
    private void fireInstanceStart(String instanceId, Map<String, Object> variables) {
        if (eventListeners == null) return;
        for (FlowEventListener listener : eventListeners) {
            try {
                listener.onInstanceStart(instanceId, variables);
            } catch (Exception e) {
                log.warn("[SubProcess] onInstanceStart 事件失败: instanceId={} err={}",
                        instanceId, e.getMessage());
            }
        }
    }

    /**
     * 发布 Spring 异步事件
     *
     * @param eventType    事件类型
     * @param childInstanceId 子流程实例 ID
     * @param parentInstanceId 父流程实例 ID
     */
    private void publishWorkflowEvent(String eventType, String childInstanceId, String parentInstanceId) {
        if (eventPublisher == null) return;
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("childInstanceId", childInstanceId);
            data.put("parentInstanceId", parentInstanceId);
            eventPublisher.publishEvent(new FlowWorkflowEvent(this, eventType, parentInstanceId, null, data));
        } catch (Exception e) {
            log.warn("[SubProcess] 发布 Spring 事件失败: type={} err={}", eventType, e.getMessage());
        }
    }

    private Map<String, Object> parseVariables(String variableJson) {
        if (variableJson == null || variableJson.isBlank()) {
            return new HashMap<>();
        }
        try {
            Map<String, Object> map = YdszJson.parseMap(variableJson);
            return map == null ? new HashMap<>() : map;
        } catch (Exception e) {
            return new HashMap<>();
        }
    }
}
