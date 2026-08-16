package com.njydsz.workflow.server.service.impl.instance;

import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.njydsz.common.feign.NotificationClient;
import com.njydsz.common.feign.dto.RealtimePushDTO;
import com.njydsz.workflow.domain.entity.FlowRunTask;
import com.njydsz.workflow.infra.mapper.FlowRunTaskMapper;
import com.njydsz.workflow.server.service.FlowTodoCountPushService;

/**
 * 待办计数推送服务实现。
 *
 * <p>实时推送用户待办数给 WebSocket 客户端（{@code ydsz.flow.todo-count}）：
 *
 * <p>任务创建/完成/转交时触发增量推送，客户端展示顶栏红点。
 *
 * @author ydsz-team
 * @since 1.0.0
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class FlowTodoCountPushServiceImpl implements FlowTodoCountPushService {

    /** 运行时任务 Mapper，统计用户当前待办数 */
    private final FlowRunTaskMapper taskMapper;
    /** 通知中心 Feign 客户端，推送实时待办数到前端 WebSocket */
    private final NotificationClient notificationClient;

    /** 推送消息类型：待办数更新 */
    public static final String TYPE_TODO_COUNT = "TODO_COUNT";
    /** 推送消息类型：任务已分配 */
    public static final String TYPE_TASK_ASSIGNED = "TASK_ASSIGNED";
    /** 推送消息类型：任务已完成 */
    public static final String TYPE_TASK_COMPLETED = "TASK_COMPLETED";
    /** 推送消息类型：任务已驳回 */
    public static final String TYPE_TASK_REJECTED = "TASK_REJECTED";
    /** P2-7 (GAP-42): 推送消息类型：心跳保活（网关层定时驱动，确认连接存活 + 刷新待办数） */
    public static final String TYPE_HEARTBEAT = "HEARTBEAT";

    @Override
    public void pushTodoCount(String userId) {
        if (userId == null) {
            return;
        }
        try {
            long count = taskMapper.countTodoByAssignee(userId, null);
            Map<String, Object> payload = new HashMap<>();
            payload.put("userId", userId);
            payload.put("todoCount", count);
            payload.put("timestamp", System.currentTimeMillis());
            notificationClient.pushRealtime(userId, TYPE_TODO_COUNT, new RealtimePushDTO(payload));
            log.debug("[FlowPush] 推送待办数: userId={} count={}", userId, count);
        } catch (Exception e) {
            log.warn("[FlowPush] 推送待办数失败: userId={} err={}", userId, e.getMessage());
        }
    }

    @Override
    public void pushTodoCountSafe(String userId) {
        pushTodoCount(userId);
    }

    @Override
    public void pushTaskAssigned(FlowRunTask task) {
        if (task == null) {
            return;
        }
        String userId = task.getAssigneeId();
        if (userId == null) {
            return;
        }
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("userId", userId);
            payload.put("taskId", task.getId());
            payload.put("taskTitle", task.getTitle());
            payload.put("flowName", task.getFlowName());
            payload.put("nodeName", task.getNodeName());
            payload.put("businessType", task.getBusinessType());
            payload.put("businessId", task.getBusinessId());
            payload.put("dueAt", task.getDueAt());
            payload.put("timestamp", System.currentTimeMillis());
            notificationClient.pushRealtime(userId, TYPE_TASK_ASSIGNED, new RealtimePushDTO(payload));
            // 任务已分配时，同步推送最新待办数
            pushTodoCount(userId);
            log.info("[FlowPush] 推送任务分配: userId={} taskId={} flowName={}",
                    userId, task.getId(), task.getFlowName());
        } catch (Exception e) {
            log.warn("[FlowPush] 推送任务分配失败: userId={} err={}", userId, e.getMessage());
        }
    }

    @Override
    public void pushTaskCompleted(FlowRunTask task, String operatorUserId) {
        if (task == null) {
            return;
        }
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("userId", operatorUserId);
            payload.put("taskId", task.getId());
            payload.put("instanceId", task.getInstanceId());
            payload.put("flowName", task.getFlowName());
            payload.put("nodeName", task.getNodeName());
            payload.put("timestamp", System.currentTimeMillis());
            if (operatorUserId != null) {
                notificationClient.pushRealtime(operatorUserId, TYPE_TASK_COMPLETED, new RealtimePushDTO(payload));
                // 完成后同步推送最新待办数
                pushTodoCount(operatorUserId);
            }
            // 通知发起人（如果发起人 != 办理人）
            log.info("[FlowPush] 推送任务完成: taskId={} operator={}",
                    task.getId(), operatorUserId);
        } catch (Exception e) {
            log.warn("[FlowPush] 推送任务完成失败: taskId={} err={}", task.getId(), e.getMessage());
        }
    }

    @Override
    public void pushTaskRejected(FlowRunTask task, String operatorUserId, String reason) {
        if (task == null) {
            return;
        }
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("userId", operatorUserId);
            payload.put("taskId", task.getId());
            payload.put("instanceId", task.getInstanceId());
            payload.put("flowName", task.getFlowName());
            payload.put("nodeName", task.getNodeName());
            payload.put("reason", reason);
            payload.put("timestamp", System.currentTimeMillis());
            if (operatorUserId != null) {
                notificationClient.pushRealtime(operatorUserId, TYPE_TASK_REJECTED, new RealtimePushDTO(payload));
                pushTodoCount(operatorUserId);
            }
            log.info("[FlowPush] 推送任务驳回: taskId={} operator={} reason={}",
                    task.getId(), operatorUserId, reason);
        } catch (Exception e) {
            log.warn("[FlowPush] 推送任务驳回失败: taskId={} err={}", task.getId(), e.getMessage());
        }
    }

    // ============================== P2-7 (GAP-42): 心跳保活 ==============================

    /**
     * P2-7 (GAP-42): 心跳保活推送
     *
     * <p>由 WebSocket 网关层（或前端心跳定时器）定时调用，确认连接存活并刷新待办数。
     * 工作流侧不直接维护 TCP 连接，仅提供"心跳消息"下发能力（携带最新待办数），
     * 真正的 TCP 级 ping/pong 心跳由网关的 WebSocket 握手配置（如 STOMP heartbeat / ServerEndpoint KeepAlive）负责。
     *
     * @param userId 用户 ID
     */
    @Override
    public void pushHeartbeat(String userId) {
        if (userId == null) {
            return;
        }
        try {
            long count = taskMapper.countTodoByAssignee(userId, null);
            Map<String, Object> payload = new HashMap<>();
            payload.put("userId", userId);
            payload.put("todoCount", count);
            payload.put("timestamp", System.currentTimeMillis());
            notificationClient.pushRealtime(userId, TYPE_HEARTBEAT, new RealtimePushDTO(payload));
            log.debug("[FlowPush] 心跳保活推送: userId={} todoCount={}", userId, count);
        } catch (Exception e) {
            log.warn("[FlowPush] 心跳保活推送失败: userId={} err={}", userId, e.getMessage());
        }
    }
}
