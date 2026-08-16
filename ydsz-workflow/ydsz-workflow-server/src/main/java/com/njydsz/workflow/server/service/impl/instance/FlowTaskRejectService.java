package com.njydsz.workflow.server.service.impl.instance;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.event.api.DomainEvent;
import com.njydsz.common.event.api.DomainEventTypes;
import com.njydsz.common.event.publish.DomainEventPublisher;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.json.YdszJson;
import com.njydsz.workflow.domain.dto.FlowTaskOperateDTO;
import com.njydsz.workflow.domain.entity.FlowInstance;
import com.njydsz.workflow.domain.entity.FlowNode;
import com.njydsz.workflow.domain.entity.FlowRunTask;
import com.njydsz.workflow.domain.entity.FlowSkip;
import com.njydsz.workflow.domain.enums.FlowInstanceStatus;
import com.njydsz.workflow.domain.enums.FlowNodeType;
import com.njydsz.workflow.domain.enums.FlowTaskStatus;
import com.njydsz.workflow.infra.mapper.FlowInstanceMapper;
import com.njydsz.workflow.infra.mapper.FlowRunTaskMapper;
import com.njydsz.workflow.server.engine.FlowAdvancer;
import com.njydsz.workflow.server.engine.FlowDefinitionCacheService;
import com.njydsz.workflow.server.metrics.FlowMetrics;
import com.njydsz.workflow.server.service.FlowAttachmentService;
import com.njydsz.workflow.server.service.FlowInstanceService;
import com.njydsz.workflow.server.service.FlowTodoCountPushService;

/**
 * 流程任务驳回服务实现。
 *
 * <p>实现驳回操作：驳回到上一节点、驳回到发起人、驳回到指定节点，
 *
 * <p>驳回后清理未来节点的待办与未触发的定时器，保留完整轨迹。
 *
 * @author ydsz-team
 * @since 1.0.0
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class FlowTaskRejectService {

    /** 运行时任务 Mapper，查询/更新任务状态 */
    private final FlowRunTaskMapper taskMapper;
    /** 流程实例 Mapper，查询实例状态和流程变量 */
    private final FlowInstanceMapper instanceMapper;
    /** 流程推进引擎，驳回后推进到目标节点 */
    private final FlowAdvancer advancer;
    /** 流程实例服务，更新实例状态 */
    private final FlowInstanceService instanceService;
    /** 跨子 Service 共享的任务校验/审计/事件辅助 */
    private final FlowTaskSupport support;
    /** 任务归档服务，完成当前任务后写入历史任务表 */
    private final FlowTaskArchiveService archiveService;
    /** 任务事件通知服务，推送任务驳回通知 */
    private final FlowTaskNotificationService notificationService;
    /** P1-6: 审批附件服务 */
    private final FlowAttachmentService attachmentService;
    /** P1-7: 待办数 WebSocket 推送服务（可能为 null：测试环境） */
    @Lazy
    private final FlowTodoCountPushService todoCountPushService;
    /** P1-2: 流程定义缓存服务（解析 startNode 下游第一节点） */
    @Lazy
    private final FlowDefinitionCacheService definitionCacheService;
    /** P2-3: Prometheus 指标（可能为 null：测试环境） */
    private final FlowMetrics flowMetrics;
    /** 统一领域事件发布门面（可选依赖，未配置时安全降级） */
    private final ObjectProvider<DomainEventPublisher> eventPublisherProvider;

    /**
     * 驳回任务。
     *
     * <p>P1-11: 支持退回任意历史节点。
     * GAP-P0-2: 当 {@code dto.targetNodeCodes} 非空且 size > 1 时，在所有指定节点
     * 同时创建待办任务；否则降级到单节点退回（{@code dto.targetNodeCode}）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void reject(FlowTaskOperateDTO dto) {
        FlowRunTask task = support.getTaskOrThrow(dto.getTaskId());
        if (FlowTaskStatus.valueOf(task.getTaskStatus()).isFinished()) {
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("error.workflow.msg_b35e6ea3")
                .build();
        }
        LocalDateTime now = LocalDateTime.now();
        Long durationMs = task.getCreatedAt() == null
                ? null
                : Duration.between(task.getCreatedAt(), now).toMillis();
        taskMapper.completeTask(task.getId(), FlowTaskStatus.REJECTED.name(),
                dto.getComment(), now, durationMs);
        archiveService.archiveToHistory(task, FlowTaskStatus.REJECTED);

        // P1-6: 保存驳回附件
        attachmentService.saveBatch(task.getInstanceId(), task.getId(), task.getNodeCode(),
                "TASK", dto.getUserId(), dto.getUserName(), dto.getAttachments(),
                task.getTenantId(), task.getProviderTraceId());

        FlowInstance instance = instanceMapper.selectById(task.getInstanceId());
        Map<String, Object> mergedVars = mergeVariables(instance, dto.getVariables());

        // P1-2: 退回到发起人 — 解析 startNode 下游第一个节点作为退回目标
        if (Boolean.TRUE.equals(dto.getRejectToInitiator())) {
            String initiatorNodeCode = resolveInitiatorNodeCode(instance.getDefinitionId());
            if (initiatorNodeCode != null) {
                dto.setTargetNodeCode(initiatorNodeCode);
                dto.setTargetNodeCodes(null); // 覆盖多节点同退
            } else {
                log.warn("[Flow] 退回发起人失败：无法解析开始节点下游第一节点，降级到默认退回: instanceId={}",
                        instance.getId());
            }
        }

        // GAP-P0-2: 优先使用多节点同退；为空时降级到单节点（向后兼容）
        List<FlowNode> rejectTargets;
        boolean multiReject = dto.getTargetNodeCodes() != null && dto.getTargetNodeCodes().size() > 1;
        if (multiReject) {
            rejectTargets = advancer.advanceMulti(instance, task.getNodeCode(),
                    "REJECT", dto.getTargetNodeCodes(), mergedVars);
        } else {
            // 单节点退回（保持原有逻辑）
            String singleTarget = dto.getTargetNodeCodes() != null && !dto.getTargetNodeCodes().isEmpty()
                    ? dto.getTargetNodeCodes().get(0)
                    : dto.getTargetNodeCode();
            rejectTargets = advancer.advance(instance, task.getNodeCode(),
                    "REJECT", singleTarget, mergedVars);
        }
        if (rejectTargets.isEmpty()) {
            // 流程被驳回到终止状态
            instanceMapper.updateStatus(instance.getId(),
                    FlowInstanceStatus.REJECTED.name(),
                    null, null, now,
                    instance.getStartAt() == null ? null
                            : Duration.between(instance.getStartAt(), now).toMillis());
            taskMapper.cancelByInstance(instance.getId(), FlowTaskStatus.CANCELLED.name());
            notificationService.fireInstanceRejected(instance.getId(), dto.getComment());
            support.audit(task, "REJECT", dto.getUserId(), null, dto.getComment(), dto.getCommentType());
            if (flowMetrics != null) {
                flowMetrics.incTaskRejected(task.getFlowCode(), task.getNodeCode());
                flowMetrics.recordTaskDuration(task, "REJECTED");
                flowMetrics.incInstanceFinished(instance.getFlowCode(), "REJECTED");
                flowMetrics.recordInstanceDuration(instance, "REJECTED");
            }
            // P0-2: 发布 Outbox 事件 FLOW_INSTANCE_REJECTED（跨模块可靠投递）
            // 消息中心据此通知发起人"审批驳回"，携带驳回原因与驳回节点
            publishRejectedEvent(instance, dto);
            return;
        }
        instanceService.generateTasksForNodes(
                instance.getId(), rejectTargets, mergedVars);
        instanceMapper.updateStatus(instance.getId(), instance.getFlowStatus(),
                rejectTargets.get(0).getNodeCode(), rejectTargets.get(0).getNodeName(),
                null, null);
        support.audit(task, "REJECT", dto.getUserId(), null, dto.getComment(), dto.getCommentType());
        log.info("[Flow] 退回任务: taskId={} targets={} multi={}", task.getId(),
                rejectTargets.stream().map(FlowNode::getNodeCode).toList(), multiReject);
        // P1-7: WebSocket 推送任务驳回
        if (todoCountPushService != null) {
            todoCountPushService.pushTaskRejected(task, dto.getUserId(), dto.getComment());
        }
        if (flowMetrics != null) {
            flowMetrics.incTaskRejected(task.getFlowCode(), task.getNodeCode());
            flowMetrics.recordTaskDuration(task, "REJECTED");
        }
    }

    // ============================== 私有辅助 ==============================

    /**
     * P0-2: 发布 FLOW_INSTANCE_REJECTED Outbox 事件（可选依赖，未配置时安全降级）
     *
     * <p>异常不影响主流程：Outbox 投递失败仅记录 WARN 日志。
     *
     * @param instance 流程实例
     * @param dto      任务操作 DTO（携带驳回原因、操作人等）
     */
    private void publishRejectedEvent(FlowInstance instance, FlowTaskOperateDTO dto) {
        DomainEventPublisher publisher = eventPublisherProvider.getIfAvailable();
        if (publisher == null) {
            log.debug("[Flow] DomainEventPublisher 未配置，跳过 REJECTED 事件: instanceId={}", instance.getId());
            return;
        }
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("instanceId", instance.getId());
        metadata.put("flowTitle", instance.getTitle() != null ? instance.getTitle() : instance.getFlowName());
        metadata.put("flowCode", instance.getFlowCode());
        metadata.put("businessType", instance.getBusinessType() != null ? instance.getBusinessType() : "");
        metadata.put("businessId", instance.getBusinessId() != null ? instance.getBusinessId() : "");
        metadata.put("initiatorId", instance.getInitiatorId() != null ? instance.getInitiatorId() : "");
        metadata.put("rejectReason", dto.getComment() != null ? dto.getComment() : "未提供原因");
        metadata.put("operatorId", dto.getUserId() != null ? dto.getUserId() : "");
        publisher.publish(DomainEvent.builder()
                .aggregateType("FlowInstance")
                .aggregateId(instance.getId())
                .eventType(DomainEventTypes.FLOW_INSTANCE_REJECTED)
                .metadata(metadata)
                .build());
    }

    /**
     * P0-1 修复: 退回到发起人 — 解析 startNode 下游第一个审批节点作为退回目标。
     *
     * <p>原实现直接返回 startNode.getNodeCode()（开始节点本身），
     * 导致退回后不会生成有意义的待办任务。修正为沿 PASS 出边找到
     * 第一个 APPROVAL 类型节点，找不到时回退到开始节点。
     */
    private String resolveInitiatorNodeCode(String definitionId) {
        if (definitionCacheService == null || definitionId == null) {
            return null;
        }
        try {
            FlowNode startNode = definitionCacheService.getStartNode(definitionId);
            if (startNode == null) {
                return null;
            }
            // 沿 PASS 出边找下游第一个 APPROVAL 节点
            String found = findFirstApprovalNode(definitionId, startNode.getNodeCode(),
                    new HashSet<>());
            return found != null ? found : startNode.getNodeCode();
        } catch (Exception e) {
            log.warn("[Flow] 解析开始节点下游失败: definitionId={} err={}", definitionId, e.getMessage());
            return null;
        }
    }

    /**
     * P0-1 修复: BFS 遍历，找定义中从指定节点出发可达的第一个 APPROVAL 节点。
     *
     * @param definitionId  流程定义 ID
     * @param startNodeCode 遍历起点
     * @param visited       已访问节点（防环路）
     * @return 第一个 APPROVAL 节点编码，未找到返回 null
     */
    private String findFirstApprovalNode(String definitionId, String startNodeCode,
                                          Set<String> visited) {
        Queue<String> queue = new ArrayDeque<>();
        queue.add(startNodeCode);
        visited.add(startNodeCode);
        while (!queue.isEmpty()) {
            String currentCode = queue.poll();
            List<FlowSkip> skips = definitionCacheService.getSkipsByNodeCode(definitionId, currentCode);
            for (FlowSkip skip : skips) {
                String nextCode = skip.getNextNodeCode();
                if (nextCode == null || visited.contains(nextCode)) {
                    continue;
                }
                visited.add(nextCode);
                FlowNode nextNode = definitionCacheService.getNodeByCode(definitionId, nextCode);
                if (nextNode != null
                        && nextNode.getNodeType() == FlowNodeType.APPROVAL.getCode()) {
                    return nextCode;
                }
                // 跳过 CC/SERVICE/END 等非审批节点，继续 BFS
                if (nextNode != null
                        && nextNode.getNodeType() != FlowNodeType.END.getCode()) {
                    queue.add(nextCode);
                }
            }
        }
        return null;
    }

    /**
     * 合并流程变量：实例已有变量 + dto 增量。
     */
    private Map<String, Object> mergeVariables(FlowInstance instance, Map<String, Object> extra) {
        if (instance == null || !StringUtils.hasText(instance.getVariable())) {
            return extra == null ? Collections.emptyMap() : extra;
        }
        try {
            Map<String, Object> base = YdszJson.parseMap(instance.getVariable());
            if (extra != null && !extra.isEmpty()) {
                base.putAll(extra);
            }
            return base;
        } catch (Exception e) {
            return extra == null ? Collections.emptyMap() : extra;
        }
    }
}
