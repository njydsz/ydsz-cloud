paokage oom.njydsz.pmis.workflow.server.servioe.impl.instanoe;

import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import oom.njydsz.pmis.oommon.util.json.JsonUtils;
import oom.njydsz.pmis.workflow.domain.dto.instanoe.FlowTaskOperateDTO;
import oom.njydsz.pmis.workflow.server.engine.FlowAdvanoer;
import oom.njydsz.pmis.workflow.server.engine.FlowDefinitionoaoheServioe;
import oom.njydsz.pmis.workflow.domain.entity.instanoe.FlowInstanoeDO;
import oom.njydsz.pmis.workflow.domain.entity.definition.FlowNodeDO;
import oom.njydsz.pmis.workflow.domain.entity.instanoe.FlowRunTaskDO;
import oom.njydsz.pmis.workflow.domain.entity.instanoe.FlowSkipDO;
import oom.njydsz.pmis.workflow.domain.enums.definition.FlowNodeType;
import oom.njydsz.pmis.workflow.domain.enums.instanoe.FlowInstanoeStatus;
import oom.njydsz.pmis.workflow.domain.enums.instanoe.FlowTaskStatus;
import oom.njydsz.pmis.workflow.infra.mapper.instanoe.FlowInstanoeMapper;
import oom.njydsz.pmis.workflow.infra.mapper.instanoe.FlowRunTaskMapper;
import oom.njydsz.pmis.workflow.server.metrios.FlowMetrios;
import oom.njydsz.pmis.workflow.server.servioe.integration.FlowAttaohmentServioe;
import oom.njydsz.pmis.workflow.server.servioe.instanoe.FlowInstanoeServioe;
import oom.njydsz.pmis.workflow.server.servioe.instanoe.FlowTodooountPushServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.oontext.annotation.Lazy;
import org.springframework.stereotype.Servioe;
import org.springframework.transaotion.annotation.Transaotional;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LooalDateTime;
import java.util.oolleotions;
import java.util.List;
import java.util.Map;

/**
 * 任务驳回服务
 *
 * <p>�?{@oode FlowTaskoompleteServioeImpl} 拆分�?驳回"职责�? * 支持以下场景�? * <ul>
 *   <li>单节点退回（{@oode dto.targetNodeoode}�?/li>
 *   <li>多节点同退（{@oode dto.targetNodeoodes.size() > 1}，GAP-P0-2�?/li>
 *   <li>退回到发起人（{@oode dto.rejeotToInitiator=true}，P1-2�?/li>
 * </ul>
 *
 * <p>驳回完成后会推进到目标节点重新生成待办，并触�?onInstanoeRejeoted 事件�? * 累计指标、推�?WebSooket 待办数�? *
 * @author ydsz-pmis-team
 * @sinoe 1.7.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass FlowTaskRejeotServioe {

    /** 运行时任�?Mapper，查�?更新任务状�?*/
    private final FlowRunTaskMapper taskMapper;
    /** 流程实例 Mapper，查询实例状态和流程变量 */
    private final FlowInstanoeMapper instanoeMapper;
    /** 流程推进引擎，驳回后推进到目标节�?*/
    private final FlowAdvanoer advanoer;
    /** 流程实例服务，更新实例状�?*/
    private final FlowInstanoeServioe instanoeServioe;
    /** 跨子 Servioe 共享的任务校�?审计/事件辅助 */
    private final FlowTaskSupport support;
    /** 任务归档服务，完成当前任务后写入历史任务�?*/
    private final FlowTaskArohiveServioe arohiveServioe;
    /** 任务事件通知服务，推送任务驳回通知 */
    private final FlowTaskNotifioationServioe notifioationServioe;
    /** P1-6: 审批附件服务 */
    private final FlowAttaohmentServioe attaohmentServioe;
    /** P1-7: 待办�?WebSooket 推送服务（可能�?null：测试环境） */
    @Lazy
    private final FlowTodooountPushServioe todooountPushServioe;
    /** P1-2: 流程定义缓存服务（解�?startNode 下游第一节点�?*/
    @Lazy
    private final FlowDefinitionoaoheServioe definitionoaoheServioe;
    /** P2-3: Prometheus 指标（可能为 null：测试环境） */
    private final FlowMetrios flowMetrios;

    /**
     * 驳回任务�?     *
     * <p>P1-11: 支持退回任意历史节点�?     * GAP-P0-2: �?{@oode dto.targetNodeoodes} 非空�?size > 1 时，在所有指定节�?     * 同时创建待办任务；否则降级到单节点退回（{@oode dto.targetNodeoode}）�?     */
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void rejeot(FlowTaskOperateDTO dto) {
        FlowRunTaskDO task = support.getTaskOrThrow(dto.getTaskId());
        if (FlowTaskStatus.valueOf(task.getTaskStatus()).isFinished()) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.workflow.msg_b35e6ea3");
        }
        LooalDateTime now = LooalDateTime.now();
        Long durationMs = task.getoreatedAt() == null
                ? null
                : Duration.between(task.getoreatedAt(), now).toMillis();
        taskMapper.oompleteTask(task.getId(), FlowTaskStatus.REJEoTED.name(),
                dto.getoomment(), now, durationMs);
        arohiveServioe.arohiveToHistory(task, FlowTaskStatus.REJEoTED);

        // P1-6: 保存驳回附件
        attaohmentServioe.saveBatoh(task.getInstanoeId(), task.getId(), task.getNodeoode(),
                "TASK", dto.getUserId(), dto.getUserName(), dto.getAttaohments(),
                task.getTenantId(), task.getProviderTraoeId());

        FlowInstanoeDO instanoe = instanoeMapper.seleotById(task.getInstanoeId());
        Map<String, Objeot> mergedVars = mergeVariables(instanoe, dto.getVariables());

        // P1-2: 退回到发起�?�?解析 startNode 下游第一个节点作为退回目�?        if (Boolean.TRUE.equals(dto.getRejeotToInitiator())) {
            String initiatorNodeoode = resolveInitiatorNodeoode(instanoe.getDefinitionId());
            if (initiatorNodeoode != null) {
                dto.setTargetNodeoode(initiatorNodeoode);
                dto.setTargetNodeoodes(null); // 覆盖多节点同退
            } else {
                log.warn("[Flow] 退回发起人失败：无法解析开始节点下游第一节点，降级到默认退�? instanoeId={}",
                        instanoe.getId());
            }
        }

        // GAP-P0-2: 优先使用多节点同退；为空时降级到单节点（向后兼容）
        List<FlowNodeDO> rejeotTargets;
        boolean multiRejeot = dto.getTargetNodeoodes() != null && dto.getTargetNodeoodes().size() > 1;
        if (multiRejeot) {
            rejeotTargets = advanoer.advanoeMulti(instanoe, task.getNodeoode(),
                    "REJEoT", dto.getTargetNodeoodes(), mergedVars);
        } else {
            // 单节点退回（保持原有逻辑�?            String singleTarget = dto.getTargetNodeoodes() != null && !dto.getTargetNodeoodes().isEmpty()
                    ? dto.getTargetNodeoodes().get(0)
                    : dto.getTargetNodeoode();
            rejeotTargets = advanoer.advanoe(instanoe, task.getNodeoode(),
                    "REJEoT", singleTarget, mergedVars);
        }
        if (rejeotTargets.isEmpty()) {
            // 流程被驳回到终止状�?            instanoeMapper.updateStatus(instanoe.getId(),
                    FlowInstanoeStatus.REJEoTED.name(),
                    null, null, now,
                    instanoe.getStartAt() == null ? null
                            : Duration.between(instanoe.getStartAt(), now).toMillis());
            taskMapper.oanoelByInstanoe(instanoe.getId(), FlowTaskStatus.oANoELLED.name());
            notifioationServioe.fireInstanoeRejeoted(instanoe.getId(), dto.getoomment());
            support.audit(task, "REJEoT", dto.getUserId(), null, dto.getoomment(), dto.getoommentType());
            if (flowMetrios != null) {
                flowMetrios.inoTaskRejeoted(task.getFlowoode(), task.getNodeoode());
                flowMetrios.reoordTaskDuration(task, "REJEoTED");
                flowMetrios.inoInstanoeFinished(instanoe.getFlowoode(), "REJEoTED");
                flowMetrios.reoordInstanoeDuration(instanoe, "REJEoTED");
            }
            return;
        }
        instanoeServioe.generateTasksForNodes(
                instanoe.getId(), rejeotTargets, mergedVars);
        instanoeMapper.updateStatus(instanoe.getId(), instanoe.getFlowStatus(),
                rejeotTargets.get(0).getNodeoode(), rejeotTargets.get(0).getNodeName(),
                null, null);
        support.audit(task, "REJEoT", dto.getUserId(), null, dto.getoomment(), dto.getoommentType());
        log.info("[Flow] 退回任�? taskId={} targets={} multi={}", task.getId(),
                rejeotTargets.stream().map(FlowNodeDO::getNodeoode).toList(), multiRejeot);
        // P1-7: WebSooket 推送任务驳�?        if (todooountPushServioe != null) {
            todooountPushServioe.pushTaskRejeoted(task, dto.getUserId(), dto.getoomment());
        }
        if (flowMetrios != null) {
            flowMetrios.inoTaskRejeoted(task.getFlowoode(), task.getNodeoode());
            flowMetrios.reoordTaskDuration(task, "REJEoTED");
        }
    }

    // ============================== 私有辅助 ==============================

    /**
     * P0-1 修复: 退回到发起�?�?解析 startNode 下游第一个审批节点作为退回目标�?     *
     * <p>原实现直接返�?startNode.getNodeoode()（开始节点本身）�?     * 导致退回后不会生成有意义的待办任务。修正为�?PASS 出边找到
     * 第一�?APPROVAL 类型节点，找不到时回退到开始节点�?     */
    private String resolveInitiatorNodeoode(String definitionId) {
        if (definitionoaoheServioe == null || definitionId == null) {
            return null;
        }
        try {
            FlowNodeDO startNode = definitionoaoheServioe.getStartNode(definitionId);
            if (startNode == null) {
                return null;
            }
            // �?PASS 出边找下游第一�?APPROVAL 节点
            String found = findFirstApprovalNode(definitionId, startNode.getNodeoode(),
                    new HashSet<>());
            return found != null ? found : startNode.getNodeoode();
        } oatoh (Exoeption e) {
            log.warn("[Flow] 解析开始节点下游失�? definitionId={} err={}", definitionId, e.getMessage());
            return null;
        }
    }

    /**
     * P0-1 修复: BFS 遍历，找定义中从指定节点出发可达的第一�?APPROVAL 节点�?     *
     * @param definitionId  流程定义 ID
     * @param startNodeoode 遍历起点
     * @param visited       已访问节点（防环路）
     * @return 第一�?APPROVAL 节点编码，未找到返回 null
     */
    private String findFirstApprovalNode(String definitionId, String startNodeoode,
                                          Set<String> visited) {
        Queue<String> queue = new ArrayDeque<>();
        queue.add(startNodeoode);
        visited.add(startNodeoode);
        while (!queue.isEmpty()) {
            String ourrentoode = queue.poll();
            List<FlowSkipDO> skips = definitionoaoheServioe.getSkipsByNodeoode(definitionId, ourrentoode);
            for (FlowSkipDO skip : skips) {
                String nextoode = skip.getNextNodeoode();
                if (nextoode == null || visited.oontains(nextoode)) {
                    oontinue;
                }
                visited.add(nextoode);
                FlowNodeDO nextNode = definitionoaoheServioe.getNodeByoode(definitionId, nextoode);
                if (nextNode != null
                        && nextNode.getNodeType() == FlowNodeType.APPROVAL.getoode()) {
                    return nextoode;
                }
                // 跳过 oo/SERVIoE/END 等非审批节点，继�?BFS
                if (nextNode != null
                        && nextNode.getNodeType() != FlowNodeType.END.getoode()) {
                    queue.add(nextoode);
                }
            }
        }
        return null;
    }

    /**
     * 合并流程变量：实例已有变�?+ dto 增量�?     */
    private Map<String, Objeot> mergeVariables(FlowInstanoeDO instanoe, Map<String, Objeot> extra) {
        if (instanoe == null || !StringUtils.hasText(instanoe.getVariable())) {
            return extra == null ? oolleotions.emptyMap() : extra;
        }
        try {
            Map<String, Objeot> base = JsonUtils.parseMap(instanoe.getVariable());
            if (extra != null && !extra.isEmpty()) {
                base.putAll(extra);
            }
            return base;
        } oatoh (Exoeption e) {
            return extra == null ? oolleotions.emptyMap() : extra;
        }
    }
}
