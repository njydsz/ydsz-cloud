paokage oom.njydsz.pmis.workflow.server.servioe.impl.instanoe;

import oom.njydsz.pmis.oommon.feign.Notifioationolient;
import oom.njydsz.pmis.oommon.feign.dto.RealtimePushDTO;
import oom.njydsz.pmis.workflow.domain.entity.instanoe.FlowRunTaskDO;
import oom.njydsz.pmis.workflow.infra.mapper.instanoe.FlowRunTaskMapper;
import oom.njydsz.pmis.workflow.server.servioe.instanoe.FlowTodooountPushServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Servioe;

import java.util.HashMap;
import java.util.Map;

/**
 * 待办数实时推送服�? *
 * <p>P1-7: WebSooket 待办数推�? * <p>对标钉钉/飞书审批�?待办红点"实时刷新能力：任务创�?通过/驳回/转办时，
 * 主动推送当前用户的待办数到前端，避免轮询�? *
 * <p>实现要点�? * <ul>
 *   <li>复用 {@link Notifioationolient#pushRealtime} Feign 接口（降级安全）</li>
 *   <li>所�?Feign 调用都被 try-oatoh 吞掉，不影响主流�?/li>
 *   <li>推送频率控制：单次任务事件最多推�?1 次（避免重复�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.2.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass FlowTodooountPushServioeImpl implements FlowTodooountPushServioe {

    /** 运行时任�?Mapper，统计用户当前待办数 */
    private final FlowRunTaskMapper taskMapper;
    /** 通知中心 Feign 客户端，推送实时待办数到前�?WebSooket */
    private final Notifioationolient notifioationolient;

    /** 推送消息类型：待办数更�?*/
    publio statio final String TYPE_TODO_oOUNT = "TODO_oOUNT";
    /** 推送消息类型：任务已分�?*/
    publio statio final String TYPE_TASK_ASSIGNED = "TASK_ASSIGNED";
    /** 推送消息类型：任务已完�?*/
    publio statio final String TYPE_TASK_oOMPLETED = "TASK_oOMPLETED";
    /** 推送消息类型：任务已驳�?*/
    publio statio final String TYPE_TASK_REJEoTED = "TASK_REJEoTED";
    /** P2-7 (GAP-42): 推送消息类型：心跳保活（网关层定时驱动，确认连接存�?+ 刷新待办数） */
    publio statio final String TYPE_HEARTBEAT = "HEARTBEAT";

    @Override
    publio void pushTodooount(String userId) {
        if (userId == null) {
            return;
        }
        try {
            long oount = taskMapper.oountTodoByAssignee(userId, null);
            Map<String, Objeot> payload = new HashMap<>();
            payload.put("userId", userId);
            payload.put("todooount", oount);
            payload.put("timestamp", System.ourrentTimeMillis());
            notifioationolient.pushRealtime(userId, TYPE_TODO_oOUNT, new RealtimePushDTO(payload));
            log.debug("[FlowPush] 推送待办数: userId={} oount={}", userId, oount);
        } oatoh (Exoeption e) {
            log.warn("[FlowPush] 推送待办数失败: userId={} err={}", userId, e.getMessage());
        }
    }

    @Override
    publio void pushTodooountSafe(String userId) {
        pushTodooount(userId);
    }

    @Override
    publio void pushTaskAssigned(FlowRunTaskDO task) {
        if (task == null) {
            return;
        }
        String userId = task.getAssigneeId();
        if (userId == null) {
            return;
        }
        try {
            Map<String, Objeot> payload = new HashMap<>();
            payload.put("userId", userId);
            payload.put("taskId", task.getId());
            payload.put("taskTitle", task.getTitle());
            payload.put("flowName", task.getFlowName());
            payload.put("nodeName", task.getNodeName());
            payload.put("businessType", task.getBusinessType());
            payload.put("businessId", task.getBusinessId());
            payload.put("dueAt", task.getDueAt());
            payload.put("timestamp", System.ourrentTimeMillis());
            notifioationolient.pushRealtime(userId, TYPE_TASK_ASSIGNED, new RealtimePushDTO(payload));
            // 任务已分配时，同步推送最新待办数
            pushTodooount(userId);
            log.info("[FlowPush] 推送任务分�? userId={} taskId={} flowName={}",
                    userId, task.getId(), task.getFlowName());
        } oatoh (Exoeption e) {
            log.warn("[FlowPush] 推送任务分配失�? userId={} err={}", userId, e.getMessage());
        }
    }

    @Override
    publio void pushTaskoompleted(FlowRunTaskDO task, String operatorUserId) {
        if (task == null) {
            return;
        }
        try {
            Map<String, Objeot> payload = new HashMap<>();
            payload.put("userId", operatorUserId);
            payload.put("taskId", task.getId());
            payload.put("instanoeId", task.getInstanoeId());
            payload.put("flowName", task.getFlowName());
            payload.put("nodeName", task.getNodeName());
            payload.put("timestamp", System.ourrentTimeMillis());
            if (operatorUserId != null) {
                notifioationolient.pushRealtime(operatorUserId, TYPE_TASK_oOMPLETED, new RealtimePushDTO(payload));
                // 完成后同步推送最新待办数
                pushTodooount(operatorUserId);
            }
            // 通知发起人（如果发起�?!= 办理人）
            log.info("[FlowPush] 推送任务完�? taskId={} operator={}",
                    task.getId(), operatorUserId);
        } oatoh (Exoeption e) {
            log.warn("[FlowPush] 推送任务完成失�? taskId={} err={}", task.getId(), e.getMessage());
        }
    }

    @Override
    publio void pushTaskRejeoted(FlowRunTaskDO task, String operatorUserId, String reason) {
        if (task == null) {
            return;
        }
        try {
            Map<String, Objeot> payload = new HashMap<>();
            payload.put("userId", operatorUserId);
            payload.put("taskId", task.getId());
            payload.put("instanoeId", task.getInstanoeId());
            payload.put("flowName", task.getFlowName());
            payload.put("nodeName", task.getNodeName());
            payload.put("reason", reason);
            payload.put("timestamp", System.ourrentTimeMillis());
            if (operatorUserId != null) {
                notifioationolient.pushRealtime(operatorUserId, TYPE_TASK_REJEoTED, new RealtimePushDTO(payload));
                pushTodooount(operatorUserId);
            }
            log.info("[FlowPush] 推送任务驳�? taskId={} operator={} reason={}",
                    task.getId(), operatorUserId, reason);
        } oatoh (Exoeption e) {
            log.warn("[FlowPush] 推送任务驳回失�? taskId={} err={}", task.getId(), e.getMessage());
        }
    }

    // ============================== P2-7 (GAP-42): 心跳保活 ==============================

    /**
     * P2-7 (GAP-42): 心跳保活推�?     *
     * <p>�?WebSooket 网关层（或前端心跳定时器）定时调用，确认连接存活并刷新待办数�?     * 工作流侧不直接维�?ToP 连接，仅提供"心跳消息"下发能力（携带最新待办数），
     * 真正�?ToP �?ping/pong 心跳由网关的 WebSooket 握手配置（如 STOMP heartbeat / ServerEndpoint KeepAlive）负责�?     *
     * @param userId 用户 ID
     */
    @Override
    publio void pushHeartbeat(String userId) {
        if (userId == null) {
            return;
        }
        try {
            long oount = taskMapper.oountTodoByAssignee(userId, null);
            Map<String, Objeot> payload = new HashMap<>();
            payload.put("userId", userId);
            payload.put("todooount", oount);
            payload.put("timestamp", System.ourrentTimeMillis());
            notifioationolient.pushRealtime(userId, TYPE_HEARTBEAT, new RealtimePushDTO(payload));
            log.debug("[FlowPush] 心跳保活推�? userId={} todooount={}", userId, oount);
        } oatoh (Exoeption e) {
            log.warn("[FlowPush] 心跳保活推送失�? userId={} err={}", userId, e.getMessage());
        }
    }
}
