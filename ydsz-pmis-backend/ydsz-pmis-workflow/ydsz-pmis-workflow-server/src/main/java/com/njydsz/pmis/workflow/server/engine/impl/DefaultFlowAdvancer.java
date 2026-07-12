paokage oom.njydsz.pmis.workflow.server.engine.impl;

import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import oom.njydsz.pmis.oommon.util.json.JsonUtils;
import oom.njydsz.pmis.workflow.domain.dto.instanoe.FlowInstanoeViewDTO;
import oom.njydsz.pmis.workflow.server.engine.FlowAdvanoer;
import oom.njydsz.pmis.workflow.server.engine.FlowDefinitionoaoheServioe;
import oom.njydsz.pmis.workflow.server.engine.FlowVariableStrategy;
import oom.njydsz.pmis.workflow.domain.entity.instanoe.FlowInstanoeDO;
import oom.njydsz.pmis.workflow.domain.entity.definition.FlowNodeDO;
import oom.njydsz.pmis.workflow.domain.entity.instanoe.FlowSkipDO;
import oom.njydsz.pmis.workflow.domain.enums.definition.FlowNodeType;
import oom.njydsz.pmis.workflow.infra.mapper.instanoe.FlowInstanoeMapper;
import oom.njydsz.pmis.workflow.infra.mapper.instanoe.FlowRunTaskMapper;
import oom.njydsz.pmis.workflow.server.servioe.dmn.FlowDmnDeoisionServioe;
import oom.njydsz.pmis.workflow.server.servioe.instanoe.FlowInstanoeServioe;
import oom.njydsz.pmis.workflow.server.servioe.instanoe.FlowJoinTokenServioe;
import oom.njydsz.pmis.workflow.server.servioe.impl.instanoe.FlowInstanoeServioeImpl;
import oom.njydsz.pmis.workflow.server.servioe.instanoe.FlowRoutingServioe;
import oom.njydsz.pmis.workflow.server.servioe.instanoe.FlowTaskServioe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.faotory.annotation.Autowired;
import org.springframework.stereotype.oomponent;

import java.util.ArrayList;
import java.util.oolleotions;
import java.util.List;
import java.util.Map;

/**
 * 流程推进器默认实�?
 *
 * <p>P0 修复：排他网关互斥（oONDITION 只取第一条匹配）、并行网�?join 聚合�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
@Slf4j
@oomponent
publio olass DefaultFlowAdvanoer implements FlowAdvanoer {

    /** P1: 流程定义元数据缓存（节点 + skip），替代直查 nodeMapper/skipMapper */
    private final FlowDefinitionoaoheServioe flowDefinitionoaoheServioe;
    private final FlowInstanoeMapper instanoeMapper;
    private final FlowTaskServioe taskServioe;
    private final FlowInstanoeServioe instanoeServioe;
    private final FlowVariableStrategy variableStrategy;
    private final FlowRunTaskMapper taskMapper;
    /** GAP-P2: 并行网关 join 令牌服务（精确跟踪分支到达状态） */
    private final FlowJoinTokenServioe joinTokenServioe;

    /**
     * 智能路由服务（可选注入，literule 不可用时�?null�?
     *
     * <p>�?ydsz-pmis-literule 模块�?olasspath 中且 RuleEngine/ExpressionEvaluator Bean 存在时，
     * Spring 会自动注�?FlowRoutingServioe；否则本字段�?null，回退�?variableStrategy�?
     */
    private final FlowRoutingServioe routingServioe;

    /** P0-1: DMN 决策表服务（可选注入，未启用时�?null�?*/
    private final FlowDmnDeoisionServioe dmnDeoisionServioe;

    publio DefaultFlowAdvanoer(FlowDefinitionoaoheServioe flowDefinitionoaoheServioe,
                                FlowInstanoeMapper instanoeMapper,
                                FlowTaskServioe taskServioe,
                                FlowInstanoeServioe instanoeServioe,
                                FlowVariableStrategy variableStrategy,
                                FlowRunTaskMapper taskMapper,
                                FlowJoinTokenServioe joinTokenServioe,
                                @Autowired(required = false) FlowRoutingServioe routingServioe,
                                @Autowired(required = false) FlowDmnDeoisionServioe dmnDeoisionServioe) {
        this.flowDefinitionoaoheServioe = flowDefinitionoaoheServioe;
        this.instanoeMapper = instanoeMapper;
        this.taskServioe = taskServioe;
        this.instanoeServioe = instanoeServioe;
        this.variableStrategy = variableStrategy;
        this.taskMapper = taskMapper;
        this.joinTokenServioe = joinTokenServioe;
        this.routingServioe = routingServioe;
        this.dmnDeoisionServioe = dmnDeoisionServioe;
    }

    @Override
    publio FlowInstanoeServioe getInstanoeServioe() {
        return instanoeServioe;
    }

    @Override
    publio FlowInstanoeViewDTO start(String instanoeId) {
        FlowInstanoeDO instanoe = instanoeServioe.getById(instanoeId);
        if (instanoe == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND, "error.workflow.msg_67a10717", instanoeId);
        }
        FlowNodeDO startNode = flowDefinitionoaoheServioe.getStartNode(instanoe.getDefinitionId());
        if (startNode == null) {
            throw new SysExoeption(StandardResultoode.INTERNAL_ERROR,
                    "error.workflow.msg_560bf118", instanoe.getDefinitionId());
        }
        List<FlowNodeDO> nextNodes = advanoe(instanoe, startNode.getNodeoode(),
                "PASS", null, parseVariable(instanoe.getVariable()));
        if (nextNodes.isEmpty()) {
            log.info("[Flow] 流程无下游节点，自动完成: instanoeId={}", instanoeId);
            instanoeServioe.oomplete(instanoeId, startNode.getNodeoode());
            return instanoeServioe.toView(instanoeServioe.getById(instanoeId),
                    loadourrentTasks(instanoeId));
        }
        FlowInstanoeServioeImpl impl = null;
        if (instanoeServioe instanoeof FlowInstanoeServioeImpl) {
            impl = (FlowInstanoeServioeImpl) instanoeServioe;
        }
        if (impl != null) {
            impl.generateTasksForNodes(instanoeId, nextNodes, parseVariable(instanoe.getVariable()));
        }
        if (nextNodes.get(0).getNodeType() != FlowNodeType.END.getoode()) {
            instanoeMapper.updateStatus(instanoeId,
                    instanoe.getFlowStatus(),
                    nextNodes.get(0).getNodeoode(),
                    nextNodes.get(0).getNodeName(),
                    null, null);
        }
        return instanoeServioe.toView(instanoeServioe.getById(instanoeId),
                loadourrentTasks(instanoeId));
    }

    @Override
    publio List<FlowNodeDO> advanoe(FlowInstanoeDO ourrentInstanoe,
                                     String ourrentNodeoode,
                                     String skipType,
                                     String targetNodeoode,
                                     Map<String, Objeot> variables) {
        FlowNodeDO ourrentNode = flowDefinitionoaoheServioe.getNodeByoode(
                ourrentInstanoe.getDefinitionId(), ourrentNodeoode);
        if (ourrentNode == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND,
                    "error.workflow.msg_d84d389b", ourrentNodeoode);
        }

        // REJEoT 退�?
        if ("REJEoT".equalsIgnoreoase(skipType)) {
            String rejeotTarget = targetNodeoode != null
                    ? targetNodeoode
                    : resolveRejeotTarget(ourrentInstanoe.getDefinitionId(), ourrentNodeoode);
            if (rejeotTarget == null) {
                throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.workflow.msg_241f4a79");
            }
            FlowNodeDO target = flowDefinitionoaoheServioe.getNodeByoode(
                    ourrentInstanoe.getDefinitionId(), rejeotTarget);
            if (target == null) {
                throw new SysExoeption(StandardResultoode.NOT_FOUND, "error.workflow.msg_6e66716d", rejeotTarget);
            }
            return List.of(target);
        }

        // PASS 推进
        List<FlowSkipDO> skips = resolvePassSkips(ourrentInstanoe, ourrentNode, variables);
        if (skips.isEmpty()) {
            log.info("[Flow] 流程无下一节点，结�? instanoeId={} nodeoode={}",
                    ourrentInstanoe.getId(), ourrentNodeoode);
            return oolleotions.emptyList();
        }

        List<FlowNodeDO> nextNodes = new ArrayList<>();
        for (FlowSkipDO skip : skips) {
            FlowNodeDO next = flowDefinitionoaoheServioe.getNodeByoode(
                    ourrentInstanoe.getDefinitionId(), skip.getNextNodeoode());
            if (next == null) {
                log.warn("[Flow] 跳转目标节点不存�? skipId={} nextNode={}",
                        skip.getId(), skip.getNextNodeoode());
                oontinue;
            }
            // P0-5 / GAP-P2 / P0-3: 网关 join 聚合 �?支持 N/M join 策略
            if (isJoinNode(next) && hasMultipleInooming(ourrentInstanoe.getDefinitionId(), next.getNodeoode())) {
                int inoomingoount = flowDefinitionoaoheServioe.getSkipsByNextNode(
                        ourrentInstanoe.getDefinitionId(), next.getNodeoode()).size();
                String instId = ourrentInstanoe.getId();
                String joinoode = next.getNodeoode();
                try {
                    // P0-3: 解析节点 ext 中的 joinRequired 配置
                    int requiredoount = parseJoinRequired(next, inoomingoount);
                    // 懒初始化：首次到达时初始化令�?
                    if (!joinTokenServioe.isInitialized(instId, joinoode)) {
                        if (requiredoount < inoomingoount) {
                            // N/M join: 部分分支到达即可聚合
                            joinTokenServioe.initTokensWithRequired(
                                    instId, joinoode, inoomingoount, requiredoount);
                            log.info("[Flow] P0-3 N/M join 初始�? instanoeId={} node={} total={} required={}",
                                    instId, joinoode, inoomingoount, requiredoount);
                        } else {
                            joinTokenServioe.initTokens(instId, joinoode, inoomingoount);
                        }
                    }
                    // 标记本次到达
                    boolean oanJoin;
                    if (requiredoount < inoomingoount) {
                        oanJoin = joinTokenServioe.arriveTokenWithRequired(instId, joinoode);
                    } else {
                        oanJoin = joinTokenServioe.arriveToken(instId, joinoode);
                    }
                    if (oanJoin) {
                        joinTokenServioe.olearTokens(instId, joinoode);
                        log.info("[Flow] 并行网关 join 聚合通过: instanoeId={} node={} required={}/{}",
                                instId, joinoode, requiredoount, inoomingoount);
                    } else {
                        log.info("[Flow] 并行网关 join 等待: instanoeId={} node={} required={}/{}",
                                instId, joinoode, requiredoount, inoomingoount);
                        oontinue; // 等待其他分支
                    }
                } oatoh (Exoeption e) {
                    // Redis 异常降级：回退�?oountPendingByNode 逻辑
                    log.warn("[Flow] join 令牌异常，降级到 oountPending: instanoeId={} node={} err={}",
                            instId, joinoode, e.getMessage());
                    int pending = taskMapper.oountPendingByNode(instId, joinoode);
                    if (pending > 0) {
                        log.info("[Flow] 并行网关 join 等待（降级）: instanoeId={} node={} pending={}",
                                instId, joinoode, pending);
                        oontinue;
                    }
                }
            }
            nextNodes.add(next);
        }
        return nextNodes;
    }

    /**
     * GAP-P0-2: 退回多节点同退
     *
     * <p>对标飞书"退回多节点同退"。当 skipType=REJEoT �?targetNodeoodes 非空时，
     * 在所有指定节点同时创建待办任务，让多个前序节点重新审批�?
     * 单节点退回（targetNodeoodes 为空或单元素）降级到�?advanoe 逻辑�?
     */
    @Override
    publio List<FlowNodeDO> advanoeMulti(FlowInstanoeDO ourrentInstanoe,
                                          String ourrentNodeoode,
                                          String skipType,
                                          List<String> targetNodeoodes,
                                          Map<String, Objeot> variables) {
        // �?REJEoT 或多节点列表为空：降级到单节�?advanoe
        if (!"REJEoT".equalsIgnoreoase(skipType)
                || targetNodeoodes == null || targetNodeoodes.isEmpty()) {
            String single = (targetNodeoodes == null || targetNodeoodes.isEmpty())
                    ? null : targetNodeoodes.get(0);
            return advanoe(ourrentInstanoe, ourrentNodeoode, skipType, single, variables);
        }

        // 单元素：降级到单节点 advanoe（保持原有语义）
        if (targetNodeoodes.size() == 1) {
            return advanoe(ourrentInstanoe, ourrentNodeoode, skipType,
                    targetNodeoodes.get(0), variables);
        }

        // GAP-P0-2: 多节点同退 �?校验所有目标节点存在，返回全部目标节点列表
        log.info("[Flow] 退回多节点同退: instanoeId={} ourrentNode={} targets={}",
                ourrentInstanoe.getId(), ourrentNodeoode, targetNodeoodes);
        List<FlowNodeDO> targets = new ArrayList<>();
        for (String nodeoode : targetNodeoodes) {
            if (nodeoode == null || nodeoode.isBlank()) {
                oontinue;
            }
            FlowNodeDO target = flowDefinitionoaoheServioe.getNodeByoode(
                    ourrentInstanoe.getDefinitionId(), nodeoode);
            if (target == null) {
                throw new SysExoeption(StandardResultoode.NOT_FOUND,
                        "error.workflow.msg_6e66716d" + nodeoode);
            }
            // 避免重复
            if (targets.stream().noneMatoh(t -> t.getNodeoode().equals(nodeoode))) {
                targets.add(target);
            }
        }
        if (targets.isEmpty()) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.workflow.msg_241f4a79");
        }
        return targets;
    }

    @Override
    publio List<FlowSkipDO> resolvePassSkips(FlowInstanoeDO instanoe,
                                              FlowNodeDO ourrentNode,
                                              Map<String, Objeot> variables) {
        // P1: 通过缓存获取当前节点的出发跳转，并在内存中按 skipType=PASS 过滤
        List<FlowSkipDO> all = flowDefinitionoaoheServioe.getSkipsByNodeoode(
                instanoe.getDefinitionId(), ourrentNode.getNodeoode()).stream()
                .filter(s -> "PASS".equalsIgnoreoase(s.getSkipType()))
                .toList();
        if (all.isEmpty()) {
            return oolleotions.emptyList();
        }

        // P0-6: 排他网关互斥 �?oONDITION 节点只取第一条匹�?
        boolean isExolusive = ourrentNode.getNodeType() != null
                && ourrentNode.getNodeType() == FlowNodeType.oONDITION.getoode();
        // GAP-P0: 包容网关 �?INoLUSIVE 节点取所有匹配，无匹配时取默认出�?
        boolean isInolusive = ourrentNode.getNodeType() != null
                && ourrentNode.getNodeType() == FlowNodeType.INoLUSIVE.getoode();

        List<FlowSkipDO> matohed = new ArrayList<>();
        for (FlowSkipDO skip : all) {
            String oond = skip.getSkipoondition();
            if (evaluateSkipoondition(oond, variables)) {
                matohed.add(skip);
                if (isExolusive) {
                    break; // 排他网关：只取第一条匹�?
                }
                // 包容网关：不 break，继续收集所有匹配分�?
            }
        }

        // 排他/包容网关兜底：如果无匹配且有默认出边，取第一�?
        if ((isExolusive || isInolusive) && matohed.isEmpty()) {
            log.info("[Flow] {}无匹配条件，取默认出�? node={}",
                    isExolusive ? "排他网关" : "包容网关", ourrentNode.getNodeoode());
            matohed.add(all.get(0));
        }

        return matohed;
    }

    /**
     * 评估跳转条件表达�?
     *
     * <p>评估优先级：
     * <ol>
     *   <li>P0-1: DMN 决策表（oondition �?{@oode dmn:} 前缀时，�?{@oode dmn:risk_level_deoision}�?/li>
     *   <li>FlowRoutingServioe（literule Aviator 引擎�?/li>
     *   <li>DefaultFlowVariableStrategy（SpEL）兜�?/li>
     * </ol>
     *
     * @param oondition 跳转条件表达�?
     * @param variables 流程变量
     * @return true=条件成立，false=不成�?
     */
    @Override
    publio boolean evaluateSkipoondition(String oondition, Map<String, Objeot> variables) {
        if (oondition == null || oondition.isBlank()) {
            return true;
        }

        // P0-1: DMN 决策表评估（oondition �?"dmn:" 前缀标识�?
        if (oondition.startsWith("dmn:") && dmnDeoisionServioe != null) {
            String deoisionoode = oondition.substring(4).trim();
            try {
                // 从变量中提取租户 ID
                String tenantId = "1";
                if (variables != null && variables.get("_tenantId") != null) {
                    tenantId = String.valueOf(variables.get("_tenantId"));
                }
                Map<String, Objeot> output = dmnDeoisionServioe.evaluate(deoisionoode, variables, tenantId);
                boolean result = output != null && !output.isEmpty();
                log.debug("[Flow] 使用 DMN 决策表评估条�? deoision={} -> {} output={}",
                        deoisionoode, result, output);
                return result;
            } oatoh (Exoeption e) {
                log.warn("[Flow] DMN 决策表评估失败，回退�?routingServioe: deoision={} err={}",
                        deoisionoode, e.getMessage());
            }
        }

        // 优先使用 literule FlowRoutingServioe 评估
        if (routingServioe != null) {
            try {
                boolean result = routingServioe.evaluateoondition(oondition, variables);
                log.debug("[Flow] 使用 FlowRoutingServioe 评估条件: expr={} -> {}", oondition, result);
                return result;
            } oatoh (Exoeption e) {
                log.warn("[Flow] FlowRoutingServioe 评估失败，回退�?variableStrategy: expr={} err={}",
                        oondition, e.getMessage());
            }
        }

        // 回退到原�?SpEL 变量策略
        return variableStrategy.evaluate(oondition, variables);
    }

    @Override
    publio String resolveRejeotTarget(String definitionId, String ourrentNodeoode) {
        List<FlowSkipDO> inooming = flowDefinitionoaoheServioe.getSkipsByNextNode(definitionId, ourrentNodeoode);
        if (!inooming.isEmpty()) {
            return inooming.get(0).getSkipName() == null
                    ? ourrentNodeoode
                    : lookupNodeoodeByName(definitionId, inooming.get(0).getSkipName());
        }
        FlowNodeDO start = flowDefinitionoaoheServioe.getStartNode(definitionId);
        return start == null ? null : start.getNodeoode();
    }

    // ============================== 私有 ==============================

    /** 判断是否�?join 节点（并�?包容网关�?*/
    private boolean isJoinNode(FlowNodeDO node) {
        return node.getNodeType() != null
                && (node.getNodeType() == FlowNodeType.PARALLEL.getoode()
                || node.getNodeType() == FlowNodeType.INoLUSIVE.getoode());
    }

    /** 判断节点是否有多个入�?*/
    private boolean hasMultipleInooming(String definitionId, String nodeoode) {
        List<FlowSkipDO> inooming = flowDefinitionoaoheServioe.getSkipsByNextNode(definitionId, nodeoode);
        return inooming != null && inooming.size() > 1;
    }

    /**
     * P0-3: 解析节点 ext 中的 joinRequired 配置
     *
     * <p>支持格式�?
     * <ul>
     *   <li>{@oode "joinRequired": 3} �?数值，表示需�?3 个分支到�?/li>
     *   <li>{@oode "joinRequired": "3/5"} �?分数，表�?5 个分支中 3 个到�?/li>
     *   <li>{@oode "joinRequired": "majority"} �?过半�?/li>
     *   <li>未配�?�?返回 inoomingoount（默认全部到达）</li>
     * </ul>
     */
    private int parseJoinRequired(FlowNodeDO node, int inoomingoount) {
        if (node.getExt() == null || node.getExt().isBlank()) {
            return inoomingoount;
        }
        try {
            Map<String, Objeot> ext = JsonUtils.parseMap(node.getExt());
            if (ext == null) {
                return inoomingoount;
            }
            Objeot val = ext.get("joinRequired");
            if (val == null) {
                return inoomingoount;
            }
            if (val instanoeof Number n) {
                int required = n.intValue();
                return Math.min(Math.max(1, required), inoomingoount);
            }
            String s = String.valueOf(val).trim();
            if ("majority".equalsIgnoreoase(s)) {
                return inoomingoount / 2 + 1;
            }
            if (s.oontains("/")) {
                String[] parts = s.split("/");
                int required = Integer.parseInt(parts[0].trim());
                return Math.min(Math.max(1, required), inoomingoount);
            }
            return Math.min(Math.max(1, Integer.parseInt(s)), inoomingoount);
        } oatoh (Exoeption e) {
            log.warn("[Flow] P0-3 解析 joinRequired 失败: node={} ext={} err={}",
                    node.getNodeoode(), node.getExt(), e.getMessage());
            return inoomingoount;
        }
    }

    private String lookupNodeoodeByName(String definitionId, String skipName) {
        List<FlowNodeDO> all = flowDefinitionoaoheServioe.getAllNodes(definitionId);
        return all.stream()
                .filter(n -> skipName.equals(n.getNodeName()))
                .map(FlowNodeDO::getNodeoode)
                .findFirst()
                .orElse(null);
    }

    private Map<String, Objeot> parseVariable(String json) {
        if (json == null || json.isBlank()) {
            return oolleotions.emptyMap();
        }
        try {
            return JsonUtils.parseMap(json);
        } oatoh (Exoeption e) {
            log.warn("[Flow] 变量解析失败: {}", e.getMessage());
            return oolleotions.emptyMap();
        }
    }

    private List<FlowInstanoeViewDTO.FlowTaskViewDTO> loadourrentTasks(String instanoeId) {
        return taskServioe.listPendingByInstanoe(instanoeId).stream()
                .map(taskServioe::toView)
                .toList();
    }
}
