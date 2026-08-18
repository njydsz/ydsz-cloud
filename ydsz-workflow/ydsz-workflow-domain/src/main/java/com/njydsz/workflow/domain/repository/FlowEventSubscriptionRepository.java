package com.njydsz.workflow.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.workflow.domain.vo.FlowEventSubscriptionVO;

/**
 * 事件订阅仓储接口（domain 层契约）。
 *
 * <p>定义事件订阅（ydsz_flow_event_subscription）的持久化抽象，隔离领域模型与具体数据访问技术实现。
 * 应用层 Service 通过此接口操作事件订阅聚合，不直接依赖 MyBatis Mapper。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link FlowEventSubscriptionVO}），非 DTO / infra 实体
 *   <li>查询入参使用具体字段（instanceId / nodeCode / eventType 等）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface FlowEventSubscriptionRepository {

  /**
   * 保存事件订阅（新增）。
   *
   * @param vo 事件订阅 VO
   * @return 保存后的事件订阅 VO（含生成的 id 与审计字段）
   */
  FlowEventSubscriptionVO save(FlowEventSubscriptionVO vo);

  /**
   * 根据 ID 查询事件订阅。
   *
   * @param id 事件订阅 ID
   * @return 事件订阅 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<FlowEventSubscriptionVO> findById(String id);

  /**
   * 根据实例 ID 查询事件订阅列表。
   *
   * @param instanceId 实例 ID
   * @return 事件订阅 VO 列表
   */
  List<FlowEventSubscriptionVO> findByInstanceId(String instanceId);

  /**
   * 根据实例 ID + 节点编码查询事件订阅。
   *
   * @param instanceId 实例 ID
   * @param nodeCode 节点编码
   * @return 事件订阅 VO 列表
   */
  List<FlowEventSubscriptionVO> findByInstanceAndNode(String instanceId, String nodeCode);

  /**
   * 根据 ID 删除事件订阅。
   *
   * @param id 事件订阅 ID
   */
  void deleteById(String id);

  /**
   * 根据实例 ID 删除所有事件订阅。
   *
   * @param instanceId 实例 ID
   */
  void deleteByInstanceId(String instanceId);

  /**
   * 更新事件订阅。
   *
   * @param vo 事件订阅 VO（含 id）
   * @return 更新后的事件订阅 VO
   */
  FlowEventSubscriptionVO update(FlowEventSubscriptionVO vo);

  /**
   * 按事件类型查询等待中的事件订阅。
   *
   * <p>查询 {@code eventType = ? AND flowCode = ? AND subscriptionStatus = 'WAITING'} 的订阅列表，
   * 用于事件触发时匹配等待中的订阅。
   *
   * @param eventType 事件类型（MESSAGE / ERROR / SIGNAL）
   * @param flowCode 流程编码
   * @return 事件订阅 VO 列表
   */
  List<FlowEventSubscriptionVO> findWaitingByEvent(String eventType, String flowCode);

  /**
   * 标记事件订阅为已触发。
   *
   * <p>更新 {@code subscriptionStatus = 'COMPLETED', triggeredAt = now()}。
   *
   * @param id 事件订阅 ID
   */
  void markTriggered(String id);

  /**
   * 重置事件订阅为等待状态。
   *
   * <p>更新 {@code subscriptionStatus = 'WAITING', triggeredAt = null}，
   * 用于流程回退或重试场景。
   *
   * @param id 事件订阅 ID
   */
  void resetToWaiting(String id);
}
