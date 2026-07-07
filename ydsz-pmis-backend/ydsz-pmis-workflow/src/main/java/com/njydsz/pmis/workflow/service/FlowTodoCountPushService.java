package com.njydsz.pmis.workflow.service;

import com.njydsz.pmis.workflow.entity.FlowRunTaskDO;

/**
 * 待办数实时推送服务接口
 *
 * <p>P1-7: WebSocket 待办数推送
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
public interface FlowTodoCountPushService {

    /**
     * 推送待办数到指定用户
     *
     * <p>前端订阅路径：{@code /user/{userId}/queue/notifications}
     * 消息体形如：{@code {"type":"TODO_COUNT","data":{"userId":1001,"todoCount":5}}}
     *
     * @param userId 用户 ID
     */
    void pushTodoCount(String userId);

    /**
     * 安全推送：任何异常都被吞掉（事件回调路径使用）
     */
    void pushTodoCountSafe(String userId);

    /**
     * 推送任务已分配（包含任务详情 + 最新待办数）
     *
     * @param task 任务
     */
    void pushTaskAssigned(FlowRunTaskDO task);

    /**
     * 推送任务已完成（含最新待办数）
     *
     * @param task          任务
     * @param operatorUserId 操作人
     */
    void pushTaskCompleted(FlowRunTaskDO task, String operatorUserId);

    /**
     * 推送任务已驳回（含最新待办数）
     *
     * @param task          任务
     * @param operatorUserId 操作人
     * @param reason         驳回原因
     */
    void pushTaskRejected(FlowRunTaskDO task, String operatorUserId, String reason);

    /**
     * P2-7 (GAP-42): 心跳保活推送 — 由 WebSocket 网关层定时驱动，确认连接存活并刷新待办数
     *
     * @param userId 用户 ID
     */
    void pushHeartbeat(String userId);
}
