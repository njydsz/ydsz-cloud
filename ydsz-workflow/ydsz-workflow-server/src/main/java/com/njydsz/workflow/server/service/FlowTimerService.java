package com.njydsz.workflow.server.service;

import java.time.Duration;
import java.util.List;

import com.njydsz.workflow.domain.vo.FlowTimerVO;

/**
 * 流程定时器服务。
 *
 * <p>节点上设置的定时器（延时/边界）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface FlowTimerService {

  /**
   * 注册中间定时器（流程进入 intermediateTimer 节点时调用）
   *
   * @param instanceId 实例 ID
   * @param nodeCode 节点编码
   * @param delay 等待时长
   * @return 定时器 ID
   */
  String scheduleIntermediate(String instanceId, String nodeCode, Duration delay);

  /**
   * 注册边界定时器（userTask 创建时调用）
   *
   * @param taskId userTask ID
   * @param instanceId 实例 ID
   * @param nodeCode userTask 节点编码
   * @param delay 超时时长
   * @return 定时器 ID
   */
  String scheduleBoundary(String taskId, String instanceId, String nodeCode, Duration delay);

  /**
   * 触发单个定时器（cronjob 扫描到到点记录时调用）
   *
   * @param timer 定时器记录 VO
   * @return true=触发成功 false=已被处理
   */
  boolean fire(FlowTimerVO timer);

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
   * @param reason 取消原因
   * @return 取消条数
   */
  int cancelByInstance(String instanceId, String reason);

  /**
   * 查询实例的所有定时器
   *
   * @param instanceId 流程实例 ID
   * @return 定时器 VO 列表
   */
  List<FlowTimerVO> listByInstance(String instanceId);

  /**
   * 统计实例的 PENDING 定时器数
   *
   * @param instanceId 流程实例 ID
   * @return 当前 PENDING 状态的定时器数量
   */
  long countPending(String instanceId);
}
