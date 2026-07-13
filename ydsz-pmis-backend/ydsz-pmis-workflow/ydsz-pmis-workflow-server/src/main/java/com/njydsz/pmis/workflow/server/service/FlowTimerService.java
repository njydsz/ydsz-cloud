package com.njydsz.pmis.workflow.server.service;

import java.time.Duration;
import java.util.List;

import com.njydsz.pmis.workflow.domain.entity.FlowTimerDO;

/**
 * 工作流定时器服务
 *
 * <p>P1-2: 中间定时器 + 边界定时器。
 * <p>中间定时器：流程到达 intermediateTimer 节点后等待 N 时间再继续
 * <p>边界定时器：挂在 userTask 上，到时间未完成则触发超时分支
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
public interface FlowTimerService {

    /**
     * 注册中间定时器（流程进入 intermediateTimer 节点时调用）
     *
     * @param instanceId 实例 ID
     * @param nodeCode   节点编码
     * @param delay      等待时长
     * @return 定时器 ID
     */
    String scheduleIntermediate(String instanceId, String nodeCode, Duration delay);

    /**
     * 注册边界定时器（userTask 创建时调用）
     *
     * @param taskId     userTask ID
     * @param instanceId 实例 ID
     * @param nodeCode   userTask 节点编码
     * @param delay      超时时长
     * @return 定时器 ID
     */
    String scheduleBoundary(String taskId, String instanceId, String nodeCode, Duration delay);

    /**
     * 触发单个定时器（cronjob 扫描到到点记录时调用）
     *
     * @param timer 定时器记录
     * @return true=触发成功 false=已被处理
     */
    boolean fire(FlowTimerDO timer);

    /**
     * 扫描并触发所有到点的定时器（每 30s 一次）
     *
     * @return 触发条数
     */
    int scanAndFire();

    /**
     * 取消某 userTask 关联的所有边界定时器（userTask 完成时调用）
     *
     * @param taskId userTask ID
     * @return 取消条数
     */
    int cancelByTask(String taskId);

    /**
     * 取消某实例所有 PENDING 定时器（实例终止/驳回时调用）
     *
     * @param instanceId 实例 ID
     * @param reason     取消原因
     * @return 取消条数
     */
    int cancelByInstance(String instanceId, String reason);

    /**
     * 查询实例的所有定时器
     */
    List<FlowTimerDO> listByInstance(String instanceId);

    /**
     * 统计实例的 PENDING 定时器数
     */
    long countPending(String instanceId);
}
