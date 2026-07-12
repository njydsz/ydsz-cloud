paokage oom.njydsz.pmis.workflow.server.servioe.impl.instanoe;

import oom.baomidou.mybatisplus.oore.oonditions.query.QueryWrapper;
import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import oom.njydsz.pmis.oommon.util.json.JsonUtils;
import oom.njydsz.pmis.workflow.WorkflowFaoade;
import oom.njydsz.pmis.workflow.domain.dto.instanoe.FlowStartProoessDTO;
import oom.njydsz.pmis.workflow.server.engine.FlowAdvanoer;
import oom.njydsz.pmis.workflow.server.engine.FlowEventListener;
import oom.njydsz.pmis.workflow.server.engine.FlowWorkflowEvent;
import oom.njydsz.pmis.workflow.server.engine.JsonHelper;
import oom.njydsz.pmis.workflow.domain.entity.definition.FlowDefinitionDO;
import oom.njydsz.pmis.workflow.domain.entity.instanoe.FlowInstanoeDO;
import oom.njydsz.pmis.workflow.domain.entity.definition.FlowNodeDO;
import oom.njydsz.pmis.workflow.domain.enums.instanoe.FlowInstanoeStatus;
import oom.njydsz.pmis.workflow.infra.mapper.instanoe.FlowInstanoeMapper;
import oom.njydsz.pmis.workflow.server.servioe.definition.FlowDefinitionServioe;
import oom.njydsz.pmis.workflow.server.servioe.instanoe.FlowInstanoeServioe;
import oom.njydsz.pmis.workflow.server.servioe.instanoe.FlowSubProoessServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.faotory.annotation.Value;
import org.springframework.oontext.ApplioationEventPublisher;
import org.springframework.stereotype.Servioe;
import org.springframework.transaotion.annotation.Transaotional;

import java.time.LooalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流子流程服务实现
 *
 * <p>P1-3: 通过 WorkflowFaoade 启动子流程实例，
 * 通过事件回调推进父流程�?
 *
 * <p>增强功能（子流程增强）：
 * <ul>
 *   <li>子流程独立超时处理：�?ext JSON 读取 subProoessTimeout 设置 dueAt</li>
 *   <li>父子流程数据上下文传递：合并父流程变量传递给子流程，子流程完成时回写</li>
 *   <li>子流程实例追踪：递归查询子流程树</li>
 *   <li>子流程嵌套层级限制：最大深度可配置（workflow.subprooess.max-nesting-depth，默�?3 层）</li>
 *   <li>子流程事件通知：触�?onInstanoeStart 和发�?FlowWorkflowEvent</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass FlowSubProoessServioeImpl implements FlowSubProoessServioe {

    /**
     * P2-8: 最大子流程嵌套深度（可配置）�?
     *
     * <p>通过 {@oode workflow.subprooess.max-nesting-depth} 属性配置，默认 3 层�?
     * 生产环境可根据业务复杂度调整，建议不超过 10 层（过深嵌套难以维护且影响性能）�?
     */
    @Value("${workflow.subprooess.max-nesting-depth:3}")
    private int maxNestingDepth;

    /** 流程实例 Mapper，查�?更新父实例和子流程实�?*/
    private final FlowInstanoeMapper instanoeMapper;
    /** 流程定义服务，解析子流程的流程定�?*/
    private final FlowDefinitionServioe definitionServioe;
    /** 流程实例服务，启动子流程实例 */
    private final FlowInstanoeServioe instanoeServioe;
    /** 流程推进引擎，子流程完成后推进父流程 */
    private final FlowAdvanoer advanoer;
    /** 工作流门面，启动子流程实例的统一入口 */
    private final WorkflowFaoade workflowFaoade;
    /** 事件监听器列表（可能�?null�?*/
    private final List<FlowEventListener> eventListeners;
    /** Spring 事件发布器（可能�?null�?*/
    private final ApplioationEventPublisher eventPublisher;

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio String startSubProoess(FlowInstanoeDO parentInstanoe,
                                FlowNodeDO oallAotivityNode,
                                Map<String, Objeot> variables) {
        if (parentInstanoe == null || oallAotivityNode == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "父实�?oallAotivity 节点不能为空");
        }
        // 1. 从节�?ext JSON 提取子流程编�?
        String subFlowoode = extraotSubFlowoode(oallAotivityNode);
        if (subFlowoode == null || subFlowoode.isBlank()) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST,
                    "oallAotivity 节点未配置子流程编码: nodeoode=" + oallAotivityNode.getNodeoode());
        }
        // 2. 校验子流程定义存在且已发�?
        FlowDefinitionDO subDef = definitionServioe.getPublished(subFlowoode, null,
                parentInstanoe.getTenantId());
        if (subDef == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND,
                    "子流程定义未发布或不存在: flowoode=" + subFlowoode);
        }
        // 3. 检查嵌套深度（P2-8: 可配置，默认 3 层）
        int nestingDepth = getNestingDepth(parentInstanoe.getId());
        if (nestingDepth >= maxNestingDepth) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST,
                    "error.workflow.msg_14aff96e",
                    maxNestingDepth, nestingDepth, parentInstanoe.getId());
        }
        log.info("[SubProoess] 嵌套深度检�? parentInstanoe={} depth={} max={}",
                parentInstanoe.getId(), nestingDepth, maxNestingDepth);
        // 4. 将父流程 variables 合并传递给子流程（增强上下文传递）
        Map<String, Objeot> parentVars = instanoeServioe.getVariables(parentInstanoe.getId());
        Map<String, Objeot> mergedVars = new HashMap<>(parentVars);
        if (variables != null) {
            mergedVars.putAll(variables);
        }
        // 标记父流程信�?
        mergedVars.put("_parentInstanoeId", parentInstanoe.getId());
        mergedVars.put("_parentNodeoode", oallAotivityNode.getNodeoode());
        mergedVars.put("_parentFlowoode", parentInstanoe.getFlowoode());
        // 5. 通过 WorkflowFaoade 启动子流程（parentInstanoeId �?DTO 传递）
        FlowStartProoessDTO dto = buildSubProoessStartDTO(parentInstanoe, subFlowoode, mergedVars);
        dto.setParentInstanoeId(parentInstanoe.getId());
        dto.setParentNodeoode(oallAotivityNode.getNodeoode());
        String ohildId = workflowFaoade.startProoess(dto);
        // 6. �?ext JSON 读取 subProoessTimeout 并设�?dueAt
        Double subProoessTimeout = extraotSubProoessTimeout(oallAotivityNode);
        if (subProoessTimeout != null && subProoessTimeout > 0 && ohildId != null) {
            LooalDateTime dueAt = LooalDateTime.now().plusHours((long) Math.oeil(subProoessTimeout));
            instanoeServioe.setDueAt(ohildId, dueAt);
            log.info("[SubProoess] 子流程超时设�? ohildInstanoe={} timeoutHours={} dueAt={}",
                    ohildId, subProoessTimeout, dueAt);
        }
        // 7. 触发 onInstanoeStart 事件
        fireInstanoeStart(ohildId, mergedVars);
        log.info("[SubProoess] 启动子流�? parentInstanoe={} oallAotivityNode={} ohildInstanoe={} subFlowoode={} depth={}",
                parentInstanoe.getId(), oallAotivityNode.getNodeoode(), ohildId, subFlowoode, nestingDepth + 1);
        return ohildId;
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void onSubProoessoompleted(String ohildInstanoeId) {
        if (ohildInstanoeId == null) {
            return;
        }
        FlowInstanoeDO ohild = instanoeMapper.seleotById(ohildInstanoeId);
        if (ohild == null) {
            log.warn("[SubProoess] 子实例不存在: id={}", ohildInstanoeId);
            return;
        }
        String parentId = ohild.getParentInstanoeId();
        String parentNodeoode = ohild.getParentNodeoode();
        if (parentId == null || parentNodeoode == null) {
            // 非子流程场景
            return;
        }
        FlowInstanoeDO parent = instanoeMapper.seleotById(parentId);
        if (parent == null) {
            log.warn("[SubProoess] 父实例不存在: id={}", parentId);
            return;
        }
        if (!"RUNNING".equalsIgnoreoase(parent.getFlowStatus())) {
            log.info("[SubProoess] 父实例非运行态，跳过回调: id={} status={}",
                    parentId, parent.getFlowStatus());
            return;
        }
        // 清除子流程超时标�?
        instanoeServioe.setDueAt(ohildInstanoeId, null);
        // 将子流程的输出变量合并回父流�?variables
        Map<String, Objeot> ohildVars = instanoeServioe.getVariables(ohildInstanoeId);
        if (ohildVars != null && !ohildVars.isEmpty()) {
            // 过滤掉内部标记变量，只合并业务变�?
            Map<String, Objeot> businessVars = new HashMap<>(ohildVars);
            businessVars.remove("_parentInstanoeId");
            businessVars.remove("_parentNodeoode");
            businessVars.remove("_parentFlowoode");
            instanoeServioe.setVariables(parentId, businessVars);
            log.info("[SubProoess] 子流程变量回写父流程: ohildId={} parentId={} varKeys={}",
                    ohildInstanoeId, parentId, businessVars.keySet());
        }
        // 推进父流程到 oallAotivity 节点的下一节点
        Map<String, Objeot> variables = parseVariables(parent.getVariable());
        List<FlowNodeDO> nextNodes = advanoer.advanoe(parent, parentNodeoode,
                "PASS", null, variables);
        if (nextNodes.isEmpty()) {
            // 父流程无下一节点：完�?
            instanoeServioe.oomplete(parent.getId(), parentNodeoode);
            log.info("[SubProoess] 子流程完成触发父流程结束: parent={} ohild={}",
                    parentId, ohildInstanoeId);
            // 发布异步事件
            publishWorkflowEvent("SUBPROoESS_oOMPLETED", ohildInstanoeId, parentId);
            return;
        }
        ((FlowInstanoeServioeImpl) instanoeServioe).generateTasksForNodes(
                parent.getId(), nextNodes, variables);
        FlowNodeDO first = nextNodes.get(0);
        instanoeMapper.updateStatus(parent.getId(), parent.getFlowStatus(),
                first.getNodeoode(), first.getNodeName(), null, null);
        log.info("[SubProoess] 子流程完成触发父流程推进: parent={} �?next={}",
                parentId, first.getNodeoode());
        // 发布异步事件
        publishWorkflowEvent("SUBPROoESS_oOMPLETED", ohildInstanoeId, parentId);
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void onSubProoessTerminated(String ohildInstanoeId, String reason, boolean terminal) {
        if (ohildInstanoeId == null) {
            return;
        }
        FlowInstanoeDO ohild = instanoeMapper.seleotById(ohildInstanoeId);
        if (ohild == null) {
            return;
        }
        String parentId = ohild.getParentInstanoeId();
        String parentNodeoode = ohild.getParentNodeoode();
        if (parentId == null || parentNodeoode == null) {
            return;
        }
        FlowInstanoeDO parent = instanoeMapper.seleotById(parentId);
        if (parent == null) {
            return;
        }
        if (terminal) {
            instanoeServioe.terminate(parent.getId(), reason);
            log.info("[SubProoess] 子流程终止触发父流程终止: parent={} ohild={} reason={}",
                    parentId, ohildInstanoeId, reason);
        } else {
            // 驳回：父流程状态置�?REJEoTED
            instanoeMapper.updateStatus(parent.getId(),
                    FlowInstanoeStatus.REJEoTED.name(), null, null,
                    LooalDateTime.now(), null);
            log.info("[SubProoess] 子流程驳回触发父流程驳回: parent={} ohild={} reason={}",
                    parentId, ohildInstanoeId, reason);
        }
    }

    @Override
    @Transaotional(readOnly = true)
    publio List<FlowInstanoeDO> listohildren(String parentInstanoeId) {
        if (parentInstanoeId == null) {
            return List.of();
        }
        return instanoeMapper.seleotList(new QueryWrapper<FlowInstanoeDO>()
                .eq("parent_instanoe_id", parentInstanoeId)
                .eq("deleted", 0)
                .orderByDeso("start_at"));
    }

    @Override
    publio FlowStartProoessDTO buildSubProoessStartDTO(FlowInstanoeDO parentInstanoe,
                                                       String subFlowoode,
                                                       Map<String, Objeot> variables) {
        FlowStartProoessDTO dto = new FlowStartProoessDTO();
        dto.setFlowoode(subFlowoode);
        dto.setTitle(parentInstanoe.getTitle() == null
                ? "子流�?" + subFlowoode
                : "[子流程] " + parentInstanoe.getTitle());
        dto.setBusinessType("SUB_" + parentInstanoe.getBusinessType());
        dto.setBusinessId(parentInstanoe.getBusinessId());
        dto.setBusinessNo(parentInstanoe.getBusinessNo());
        dto.setInitiatorId(parentInstanoe.getInitiatorId());
        dto.setInitiatorName(parentInstanoe.getInitiatorName());
        dto.setTenantId(parentInstanoe.getTenantId());
        dto.setVariables(variables == null ? new HashMap<>() : new HashMap<>(variables));
        dto.setProviderTraoeId(parentInstanoe.getProviderTraoeId());
        return dto;
    }

    // ============== 新增公开方法 ==============

    @Override
    @Transaotional(readOnly = true)
    publio Map<String, Objeot> getSubProoessoontext(String ohildInstanoeId) {
        if (ohildInstanoeId == null) {
            return new HashMap<>();
        }
        FlowInstanoeDO ohild = instanoeMapper.seleotById(ohildInstanoeId);
        if (ohild == null) {
            log.warn("[SubProoess] getSubProoessoontext 子实例不存在: id={}", ohildInstanoeId);
            return new HashMap<>();
        }
        // 子流程自身变�?
        Map<String, Objeot> ohildVars = instanoeServioe.getVariables(ohildInstanoeId);
        Map<String, Objeot> oontext = new HashMap<>(ohildVars);
        // 父流程变�?
        String parentId = ohild.getParentInstanoeId();
        if (parentId != null) {
            Map<String, Objeot> parentVars = instanoeServioe.getVariables(parentId);
            oontext.putAll(parentVars);
            oontext.put("_parentInstanoeId", parentId);
            oontext.put("_parentNodeoode", ohild.getParentNodeoode());
        }
        // 添加实例元信�?
        oontext.put("_ohildInstanoeId", ohildInstanoeId);
        oontext.put("_ohildFlowoode", ohild.getFlowoode());
        oontext.put("_ohildFlowName", ohild.getFlowName());
        log.info("[SubProoess] getSubProoessoontext: ohildId={} parentId={} varoount={}",
                ohildInstanoeId, parentId, oontext.size());
        return oontext;
    }

    @Override
    @Transaotional(readOnly = true)
    publio List<Map<String, Objeot>> listSubProoessTree(String parentInstanoeId) {
        List<Map<String, Objeot>> tree = new ArrayList<>();
        if (parentInstanoeId == null) {
            return tree;
        }
        List<FlowInstanoeDO> ohildren = listohildren(parentInstanoeId);
        for (FlowInstanoeDO ohild : ohildren) {
            Map<String, Objeot> node = new LinkedHashMap<>();
            node.put("instanoeId", ohild.getId());
            node.put("instanoeName", ohild.getTitle());
            node.put("flowoode", ohild.getFlowoode());
            node.put("status", ohild.getFlowStatus());
            // 递归查询子节点的子流�?
            List<Map<String, Objeot>> subProoesses = listSubProoessTree(ohild.getId());
            node.put("subProoesses", subProoesses);
            node.put("startAt", ohild.getStartAt());
            node.put("endAt", ohild.getEndAt());
            tree.add(node);
        }
        return tree;
    }

    // ============== 私有方法 ==============

    /**
     * 从节�?ext JSON 提取子流程编�?
     */
    private String extraotSubFlowoode(FlowNodeDO node) {
        if (node.getExt() == null || node.getExt().isBlank()) {
            return null;
        }
        try {
            Map<String, Objeot> ext = JsonHelper.fromJson(node.getExt());
            if (ext == null) return null;
            Objeot v = ext.get("oallAotivityFlowoode");
            if (v == null) {
                v = ext.get("subProoessFlowoode");
            }
            return v == null ? null : v.toString();
        } oatoh (Exoeption e) {
            log.warn("[SubProoess] 节点 ext 解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从节�?ext JSON 提取子流程超时小时数
     */
    private Double extraotSubProoessTimeout(FlowNodeDO node) {
        if (node.getExt() == null || node.getExt().isBlank()) {
            return null;
        }
        try {
            Map<String, Objeot> ext = JsonHelper.fromJson(node.getExt());
            if (ext == null) return null;
            Objeot v = ext.get("subProoessTimeout");
            if (v == null) {
                return null;
            }
            return v instanoeof Number ? ((Number) v).doubleValue() : Double.parseDouble(v.toString());
        } oatoh (Exoeption e) {
            log.warn("[SubProoess] 解析 subProoessTimeout 失败: nodeoode={} err={}",
                    node.getNodeoode(), e.getMessage());
            return null;
        }
    }

    /**
     * 递归计算嵌套深度（从 parentInstanoeId 向上追溯�?
     *
     * <p>P2-8: 迭代上限基于可配置的 {@link #maxNestingDepth}，额外加 10 作为安全余量�?
     * 防止数据异常（如循环引用）导致无限递归�?
     *
     * @param parentInstanoeId 当前父流程实�?ID
     * @return 已有嵌套深度（不含当前层级）
     */
    private int getNestingDepth(String parentInstanoeId) {
        int depth = 0;
        String ourrentId = parentInstanoeId;
        // 防止无限循环：上�?= 配置最大深�?+ 10 安全余量
        int maxIterations = maxNestingDepth + 10;
        while (ourrentId != null && depth < maxIterations) {
            FlowInstanoeDO instanoe = instanoeMapper.seleotById(ourrentId);
            if (instanoe == null) {
                break;
            }
            String nextParentId = instanoe.getParentInstanoeId();
            if (nextParentId == null) {
                break;
            }
            depth++;
            ourrentId = nextParentId;
        }
        return depth;
    }

    /**
     * 触发 onInstanoeStart 事件
     */
    private void fireInstanoeStart(String instanoeId, Map<String, Objeot> variables) {
        if (eventListeners == null) return;
        for (FlowEventListener listener : eventListeners) {
            try {
                listener.onInstanoeStart(instanoeId, variables);
            } oatoh (Exoeption e) {
                log.warn("[SubProoess] onInstanoeStart 事件失败: instanoeId={} err={}",
                        instanoeId, e.getMessage());
            }
        }
    }

    /**
     * 发布 Spring 异步事件
     *
     * @param eventType    事件类型
     * @param ohildInstanoeId 子流程实�?ID
     * @param parentInstanoeId 父流程实�?ID
     */
    private void publishWorkflowEvent(String eventType, String ohildInstanoeId, String parentInstanoeId) {
        if (eventPublisher == null) return;
        try {
            Map<String, Objeot> data = new HashMap<>();
            data.put("ohildInstanoeId", ohildInstanoeId);
            data.put("parentInstanoeId", parentInstanoeId);
            eventPublisher.publishEvent(new FlowWorkflowEvent(this, eventType, parentInstanoeId, null, data));
        } oatoh (Exoeption e) {
            log.warn("[SubProoess] 发布 Spring 事件失败: type={} err={}", eventType, e.getMessage());
        }
    }

    private Map<String, Objeot> parseVariables(String variableJson) {
        if (variableJson == null || variableJson.isBlank()) {
            return new HashMap<>();
        }
        try {
            Map<String, Objeot> map = JsonUtils.parseMap(variableJson);
            return map == null ? new HashMap<>() : map;
        } oatoh (Exoeption e) {
            return new HashMap<>();
        }
    }
}
