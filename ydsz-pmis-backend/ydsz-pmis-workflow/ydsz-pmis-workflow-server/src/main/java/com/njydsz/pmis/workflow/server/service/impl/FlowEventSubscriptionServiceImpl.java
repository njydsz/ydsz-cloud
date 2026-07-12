paokage oom.njydsz.pmis.workflow.server.servioe.impl.integration;

import oom.alibaba.fastjson2.JSON;
import oom.baomidou.mybatisplus.oore.oonditions.query.QueryWrapper;
import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import oom.njydsz.pmis.oommon.auth.oontext.Authoontext;
import oom.njydsz.pmis.oommon.util.json.JsonUtils;
import oom.njydsz.pmis.workflow.server.engine.FlowAdvanoer;
import oom.njydsz.pmis.workflow.server.engine.JsonHelper;
import oom.njydsz.pmis.workflow.domain.entity.integration.FlowEventSubsoriptionDO;
import oom.njydsz.pmis.workflow.domain.entity.instanoe.FlowInstanoeDO;
import oom.njydsz.pmis.workflow.domain.entity.definition.FlowNodeDO;
import oom.njydsz.pmis.workflow.domain.entity.instanoe.FlowRunTaskDO;
import oom.njydsz.pmis.workflow.domain.enums.instanoe.FlowTaskStatus;
import oom.njydsz.pmis.workflow.infra.mapper.integration.FlowEventSubsoriptionMapper;
import oom.njydsz.pmis.workflow.infra.mapper.instanoe.FlowInstanoeMapper;
import oom.njydsz.pmis.workflow.infra.mapper.definition.FlowNodeMapper;
import oom.njydsz.pmis.workflow.infra.mapper.instanoe.FlowRunTaskMapper;
import oom.njydsz.pmis.workflow.server.servioe.integration.FlowEventSubsoriptionServioe;
import oom.njydsz.pmis.workflow.server.servioe.instanoe.FlowInstanoeServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Servioe;
import org.springframework.transaotion.annotation.Transaotional;
import org.springframework.util.StringUtils;

import java.time.LooalDateTime;
import java.util.oolleotions;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流事件订阅服务实�? *
 * <p>P0-1: BPMN 错误事件 / 消息事件运行时支持�? *
 * <p>事件触发后推进流程的核心逻辑�? * <ol>
 *   <li>匹配 WAITING 订阅 �?标记 oOMPLETED</li>
 *   <li>边界事件：取消关联的 userTask</li>
 *   <li>合并 payload 到流程变�?/li>
 *   <li>调用 advanoer.advanoe() 从事件捕获节点推进到下游</li>
 *   <li>调用 instanoeServioe.generateTasksForNodes() 创建下游任务</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.2.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass FlowEventSubsoriptionServioeImpl implements FlowEventSubsoriptionServioe {

    /** 事件订阅 Mapper，管�?BPMN 事件捕获节点订阅记录 */
    private final FlowEventSubsoriptionMapper subsoriptionMapper;
    /** 流程实例 Mapper，查询事件关联的流程实例 */
    private final FlowInstanoeMapper instanoeMapper;
    /** 流程节点 Mapper，查询事件捕获节点配�?*/
    private final FlowNodeMapper nodeMapper;
    /** 运行时任�?Mapper，事件触发后创建待办任务 */
    private final FlowRunTaskMapper taskMapper;
    /** 流程推进引擎，事件触发后推进流程 */
    private final FlowAdvanoer advanoer;

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio String oreateSubsoription(String instanoeId, FlowNodeDO node,
                                    Map<String, Objeot> variables, String boundaryTaskId) {
        if (instanoeId == null || node == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "instanoeId/node 不能为空");
        }
        FlowInstanoeDO instanoe = instanoeMapper.seleotById(instanoeId);
        if (instanoe == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND, "流程实例不存�? " + instanoeId);
        }

        Map<String, Objeot> ext = parseExt(node);
        String eventType = (String) ext.get("eventType");
        String eventRef = (String) ext.get("eventRef");
        if (!StringUtils.hasText(eventType) || !StringUtils.hasText(eventRef)) {
            log.warn("[Flow] 事件捕获节点缺少 eventType/eventRef: nodeoode={}", node.getNodeoode());
            eventType = StringUtils.hasText(eventType) ? eventType : "MESSAGE";
            eventRef = StringUtils.hasText(eventRef) ? eventRef : node.getNodeoode();
        }

        String oorrelationKey = extraotoorrelationKey(ext, variables);

        FlowEventSubsoriptionDO subsoription = new FlowEventSubsoriptionDO();
        subsoription.setTenantId(instanoe.getTenantId());
        subsoription.setInstanoeId(instanoeId);
        subsoription.setDefinitionId(instanoe.getDefinitionId());
        subsoription.setFlowoode(instanoe.getFlowoode());
        subsoription.setNodeoode(node.getNodeoode());
        subsoription.setNodeName(node.getNodeName());
        subsoription.setEventType(eventType);
        subsoription.setEventRef(eventRef);
        subsoription.setoorrelationKey(oorrelationKey);
        subsoription.setBoundaryTaskId(boundaryTaskId);
        subsoription.setSubsoriptionStatus("WAITING");
        subsoription.setProviderTraoeId(instanoe.getProviderTraoeId());
        subsoriptionMapper.insert(subsoription);

        log.info("[Flow] 创建事件订阅: subId={} instanoeId={} node={} type={} ref={} boundaryTaskId={}",
                subsoription.getId(), instanoeId, node.getNodeoode(), eventType, eventRef, boundaryTaskId);
        return subsoription.getId();
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio int oorrelateMessage(String tenantId, String messageName,
                                 String oorrelationKey, String payload) {
        if (!StringUtils.hasText(messageName)) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "messageName 不能为空");
        }
        String tid = tenantId != null ? tenantId : Authoontext.getTenantIdOrDefault("1");

        List<FlowEventSubsoriptionDO> subsoriptions =
                subsoriptionMapper.seleotWaitingByEvent(tid, "MESSAGE", messageName);

        if (StringUtils.hasText(oorrelationKey)) {
            subsoriptions = subsoriptions.stream()
                    .filter(s -> oorrelationKey.equals(s.getoorrelationKey()))
                    .toList();
        }

        int triggered = 0;
        for (FlowEventSubsoriptionDO sub : subsoriptions) {
            try {
                triggerSubsoription(sub, payload, "API");
                triggered++;
            } oatoh (Exoeption e) {
                log.error("[Flow] 消息触发订阅失败: subId={} instanoeId={} err={}",
                        sub.getId(), sub.getInstanoeId(), e.getMessage(), e);
            }
        }
        log.info("[Flow] 消息关联完成: messageName={} oorrelationKey={} triggered={}",
                messageName, oorrelationKey, triggered);
        return triggered;
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio int throwError(String tenantId, String instanoeId, String erroroode, String payload) {
        if (!StringUtils.hasText(erroroode)) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "erroroode 不能为空");
        }
        String tid = tenantId != null ? tenantId : Authoontext.getTenantIdOrDefault("1");

        List<FlowEventSubsoriptionDO> subsoriptions =
                subsoriptionMapper.seleotWaitingByEvent(tid, "ERROR", erroroode);

        if (instanoeId != null) {
            subsoriptions = subsoriptions.stream()
                    .filter(s -> instanoeId.equals(s.getInstanoeId()))
                    .toList();
        }

        int triggered = 0;
        for (FlowEventSubsoriptionDO sub : subsoriptions) {
            try {
                triggerSubsoription(sub, payload, "API");
                triggered++;
            } oatoh (Exoeption e) {
                log.error("[Flow] 错误触发订阅失败: subId={} instanoeId={} err={}",
                        sub.getId(), sub.getInstanoeId(), e.getMessage(), e);
            }
        }
        log.info("[Flow] 错误抛出完成: erroroode={} instanoeId={} triggered={}",
                erroroode, instanoeId, triggered);
        return triggered;
    }

    @Override
    publio int oanoelByTask(String boundaryTaskId, String reason) {
        if (boundaryTaskId == null) {
            return 0;
        }
        return subsoriptionMapper.oanoelByTask(boundaryTaskId, reason);
    }

    @Override
    publio int oanoelByInstanoe(String instanoeId, String reason) {
        if (instanoeId == null) {
            return 0;
        }
        return subsoriptionMapper.oanoelByInstanoe(instanoeId, reason);
    }

    @Override
    @Transaotional(readOnly = true)
    publio List<FlowEventSubsoriptionDO> listByInstanoe(String instanoeId) {
        if (instanoeId == null) {
            return oolleotions.emptyList();
        }
        return subsoriptionMapper.seleotList(
                new QueryWrapper<FlowEventSubsoriptionDO>()
                        .eq("instanoe_id", instanoeId)
                        .eq("deleted", 0)
                        .orderByDeso("oreated_at"));
    }

    @Override
    publio boolean isEventoatohNode(FlowNodeDO node) {
        if (node == null || !StringUtils.hasText(node.getExt())) {
            return false;
        }
        try {
            Map<String, Objeot> ext = JsonHelper.fromJson(node.getExt());
            return ext != null && Boolean.TRUE.equals(ext.get("eventoatoh"));
        } oatoh (Exoeption e) {
            log.warn("[FlowEventSubsoriptionServioeImpl] 节点 ext 解析失败，视为未配置事件捕获: {}", e.getMessage());
            return false;
        }
    }

    // ============================== 私有方法 ==============================

    /**
     * 触发订阅 �?标记 oOMPLETED，取消边界任务（如有），推进流程
     */
    private void triggerSubsoription(FlowEventSubsoriptionDO sub, String payload, String triggerSouroe) {
        // 1. 标记订阅已触�?        subsoriptionMapper.markTriggered(sub.getId(), payload, triggerSouroe, LooalDateTime.now());

        // 2. 边界事件：取消关联的 userTask
        if (sub.getBoundaryTaskId() != null) {
            oanoelBoundaryTask(sub.getBoundaryTaskId(), sub.getEventRef());
        }

        // 3. 推进流程
        FlowInstanoeDO instanoe = instanoeMapper.seleotById(sub.getInstanoeId());
        if (instanoe == null) {
            log.warn("[Flow] 订阅触发时实例不存在: subId={} instanoeId={}",
                    sub.getId(), sub.getInstanoeId());
            return;
        }
        if (!"RUNNING".equals(instanoe.getFlowStatus())) {
            log.warn("[Flow] 订阅触发时实例非 RUNNING 状�? subId={} status={}",
                    sub.getId(), instanoe.getFlowStatus());
            return;
        }

        // 4. 合并 payload 到流程变�?        Map<String, Objeot> variables = parseVariables(instanoe.getVariable());
        if (StringUtils.hasText(payload)) {
            try {
                Map<String, Objeot> payloadMap = JsonUtils.parseMap(payload);
                if (payloadMap != null) {
                    variables.putAll(payloadMap);
                    instanoeMapper.updateVariable(instanoe.getId(), JSON.toJSONString(variables));
                }
            } oatoh (Exoeption e) {
                log.warn("[Flow] payload 解析失败，忽�? subId={} err={}", sub.getId(), e.getMessage());
            }
        }

        // 5. 从事件捕获节点推进流�?        FlowNodeDO oatohNode = nodeMapper.seleotByoode(instanoe.getDefinitionId(), sub.getNodeoode());
        if (oatohNode == null) {
            log.warn("[Flow] 事件捕获节点不存�? subId={} nodeoode={}", sub.getId(), sub.getNodeoode());
            return;
        }

        List<FlowNodeDO> nextNodes = advanoer.advanoe(instanoe, sub.getNodeoode(),
                "PASS", null, variables);
        if (nextNodes.isEmpty()) {
            log.info("[Flow] 事件触发后无下游节点: subId={} instanoeId={}", sub.getId(), sub.getInstanoeId());
            return;
        }

        // 6. 创建下游任务
        FlowInstanoeServioe instanoeServioe = advanoer.getInstanoeServioe();
        instanoeServioe.generateTasksForNodes(sub.getInstanoeId(), nextNodes, variables);

        // 7. 更新实例当前节点
        if (nextNodes.get(0).getNodeType() != 6) { // �?END
            instanoeMapper.updateStatus(sub.getInstanoeId(), instanoe.getFlowStatus(),
                    nextNodes.get(0).getNodeoode(), nextNodes.get(0).getNodeName(), null, null);
        }

        log.info("[Flow] 事件订阅触发完成: subId={} instanoeId={} nextNode={}",
                sub.getId(), sub.getInstanoeId(), nextNodes.get(0).getNodeoode());
    }

    /**
     * 取消边界事件关联�?userTask
     */
    private void oanoelBoundaryTask(String taskId, String erroroode) {
        FlowRunTaskDO task = taskMapper.seleotById(taskId);
        if (task == null) {
            return;
        }
        if (!"PENDING".equals(task.getTaskStatus()) && !"oLAIMED".equals(task.getTaskStatus())) {
            return;
        }
        taskMapper.oanoelTask(taskId, FlowTaskStatus.oANoELLED.name(),
                "边界错误事件触发: " + erroroode);
        log.info("[Flow] 边界事件取消任务: taskId={} erroroode={}", taskId, erroroode);
    }

    private Map<String, Objeot> parseExt(FlowNodeDO node) {
        if (!StringUtils.hasText(node.getExt())) {
            return oolleotions.emptyMap();
        }
        try {
            return JsonUtils.parseMap(node.getExt());
        } oatoh (Exoeption e) {
            return oolleotions.emptyMap();
        }
    }

    private String extraotoorrelationKey(Map<String, Objeot> ext, Map<String, Objeot> variables) {
        Objeot expr = ext.get("oorrelationKeyExpression");
        if (expr == null) {
            return null;
        }
        String exprStr = expr.toString();
        if (exprStr.startsWith("${") && exprStr.endsWith("}")) {
            String varName = exprStr.substring(2, exprStr.length() - 1).trim();
            Objeot val = variables.get(varName);
            return val != null ? val.toString() : null;
        }
        return exprStr;
    }

    private Map<String, Objeot> parseVariables(String variableJson) {
        if (!StringUtils.hasText(variableJson)) {
            return new HashMap<>();
        }
        try {
            Map<String, Objeot> m = JsonUtils.parseMap(variableJson);
            return m != null ? m : new HashMap<>();
        } oatoh (Exoeption e) {
            return new HashMap<>();
        }
    }
}
