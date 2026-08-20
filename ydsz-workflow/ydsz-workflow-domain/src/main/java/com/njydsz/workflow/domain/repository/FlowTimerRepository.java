package com.njydsz.workflow.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.njydsz.workflow.domain.vo.FlowTimerVO;

/**
 * 定时器仓储接口（domain 层契约）。
 *
 * <p>定义定时器（ydsz_flow_timer）的持久化抽象，隔离领域模型与具体数据访问技术实现。
 * 应用层 Service 通过此接口操作定时器聚合，不直接依赖 MyBatis Mapper。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link FlowTimerVO}），非 DTO / infra 实体
 *   <li>查询入参使用具体字段（instanceId / taskId / nodeCode 等）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface FlowTimerRepository {

  /**
   * 保存定时器（新增）。
   *
   * @param vo 定时器 VO
   * @return 保存后的定时器 VO（含生成的 id 与审计字段）
   */
  FlowTimerVO save(FlowTimerVO vo);

  /**
   * 根据 ID 查询定时器。
   *
   * @param id 定时器 ID
   * @return 定时器 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<FlowTimerVO> findById(String id);

  /**
   * 根据任务 ID 查询定时器。
   *
   * @param taskId 任务 ID
   * @return 定时器 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<FlowTimerVO> findByTaskId(String taskId);

  /**
   * 根据实例 ID 查询定时器列表。
   *
   * @param instanceId 实例 ID
   * @return 定时器 VO 列表
   */
  List<FlowTimerVO> findByInstanceId(String instanceId);

  /**
   * 根据 ID 删除定时器。
   *
   * @param id 定时器 ID
   */
  void deleteById(String id);

  /**
   * 根据实例 ID 删除所有定时器。
   *
   * @param instanceId 实例 ID
   */
  void deleteByInstanceId(String instanceId);

  /**
   * 更新定时器。
   *
   * @param vo 定时器 VO（含 id）
   * @return 更新后的定时器 VO
   */
  FlowTimerVO update(FlowTimerVO vo);

  /**
   * 查询到期的定时器列表（待触发）。
   *
   * <p>扫描 {@code fireAt <= now AND timerStatus = 'PENDING'} 的定时器，
   * 按 fireAt 升序排列，限制返回数量。由定时器调度器调用。
   *
   * @param now 当前时间
   * @param limit 返回数量上限
   * @return 到期定时器 VO 列表
   */
  List<FlowTimerVO> findDueTimers(LocalDateTime now, int limit);

  /**
   * 标记定时器为已触发。
   *
   * <p>更新 {@code timerStatus = 'FIRED', firedAt = now()}。
   *
   * @param id 定时器 ID
   */
  void markFired(String id);

  /**
   * 按任务 ID 取消定时器（边界定时器关联的 userTask 完成时调用）。
   *
   * <p>更新 {@code timerStatus = 'CANCELLED', cancelReason = 'TASK_COMPLETED'}。
   *
   * @param taskId 任务 ID
   */
  void cancelByTask(String taskId);

  /**
   * 按实例 ID 查询定时器列表（按触发时间升序）。
   *
   * <p>与 {@link #findByInstanceId(String)} 类似，但按 fireAt 升序排列，
   * 用于定时器调度器确定下一个待触发的定时器。
   *
   * @param instanceId 实例 ID
   * @return 定时器 VO 列表
   */
  List<FlowTimerVO> findByInstanceIdOrderByFireTime(String instanceId);

  /**
   * 按实例 ID 取消所有定时器（更新状态为 CANCELLED）。
   *
   * @param instanceId 实例 ID
   * @param reason 取消原因
   * @return 受影响行数
   */
  int cancelByInstance(String instanceId, String reason);

  /**
   * 按实例 ID 统计 PENDING 状态的定时器数量。
   *
   * @param instanceId 实例 ID
   * @return PENDING 定时器数量
   */
  long countPendingByInstance(String instanceId);

  /**
   * 标记定时器为延后（重新设置触发时间）。
   *
   * <p>更新 {@code fireAt = nextTime, timerStatus = 'PENDING'}，用于循环定时器或手动延后场景。
   *
   * @param id 定时器 ID
   * @param nextTime 下次触发时间
   */
  void markSnoozed(String id, LocalDateTime nextTime);
}
