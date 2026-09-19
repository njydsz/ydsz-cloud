package com.njydsz.common.event.saga;

import java.time.Instant;
import java.util.Objects;

/**
 * Saga 编排轻量基类（F-1）
 *
 * <p>借鉴 Axon Framework 的 Saga 生命周期模型（{@code @StartSaga} / {@code @EndSaga} /
 * AssociationValue 路由），提供：
 *
 * <ul>
 *   <li>Saga 关联值路由：{@link #getAssociationKey()} + {@link #getAssociationValue()} → 匹配对应 Saga 实例
 *   <li>状态流转：通过 {@link SagaState} 枚举管理 Saga 生命周期
 *   <li>事件驱动入口：{@link #onEvent(Instant, String)} 由 SagaManager 在 OutboxMessage 到达时调用
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * // 业务模块继承 AbstractSaga，实现具体的编排逻辑
 * public class OrderCreationSaga extends AbstractSaga {
 *
 *     &#64;Override
 *     protected String getAssociationKey() {
 *         return "orderId";
 *     }
 *
 *     &#64;Override
 *     protected boolean isStartEvent(OutboxMessage message) {
 *         return "OrderCreated".equals(message.getEventType());
 *     }
 *
 *     &#64;Override
 *     protected void onStart(OutboxMessage message) {
 *         // 1. 预扣库存
 *         // 2. 锁定优惠券
 *         // 3. 写入订单表
 *         // 任一步骤失败 → 触发 compensate(message)
 *     }
 *
 *     &#64;Override
 *     protected void compensate(OutboxMessage message) {
 *         // 补偿：释放库存、解锁优惠券、订单取消
 *     }
 * }
 * }</pre>
 *
 * <p><b>约束：</b>
 *
 * <ul>
 *   <li>Saga 实例无状态（stateless），所有状态持久化由 SagaManager / ydsz-common-seata 保障
 *   <li>Saga 编排步骤应幂等（下游服务需支持幂等）
 *   <li>不可逆操作（发送消息、写入审计日志）应放在最后一步使用 {@code SagaStep.terminal()} 标注
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.19
 * @see SagaManager
 * @see SagaState
 */
public abstract class AbstractSaga {

  /** 当前 Saga 状态 */
  private SagaState state = SagaState.CREATED;

  /** Saga 创建时间 */
  private final Instant createdAt = Instant.now();

  /** 最后更新时间 */
  private Instant lastUpdatedAt = Instant.now();

  /** 关联值（如订单号），用于事件路由 */
  private String associationValue;

  /**
   * 获取关联键名（如 "orderId"）
   *
   * <p>SagaManager 根据此键名和消息 metadata 中的关联值路由到正确的 Saga 实例。
   *
   * @return 关联键名
   */
  protected abstract String getAssociationKey();

  /**
   * 判断是否为 Saga 起始事件
   *
   * <p>仅当 Saga 处于 CREATED/WAITING 状态且消息匹配起始事件类型时返回 true。
   *
   * @param eventType 事件类型
   * @return true 表示是起始事件
   */
  protected abstract boolean isStartEvent(String eventType);

  /**
   * 判断 Saga 是否已结束
   *
   * <p>COMPLETED / COMPENSATED / FAILED 三种状态均视为结束。
   *
   * @return true 表示 Saga 已完成编排
   */
  public boolean isEnded() {
    return state == SagaState.COMPLETED
        || state == SagaState.COMPENSATED
        || state == SagaState.FAILED;
  }

  /**
   * 通用事件处理入口
   *
   * <p>SagaManager 调用此方法，子类可重写以实现特定事件的编排逻辑。 默认实现根据当前状态分发到对应处理方法。
   *
   * @param eventType 事件类型
   * @param associationValue 关联值（如订单号）
   */
  public void onEvent(String eventType, String associationValue) {
    this.associationValue = associationValue;
    this.lastUpdatedAt = Instant.now();

    if (state == SagaState.CREATED && isStartEvent(eventType)) {
      state = SagaState.RUNNING;
      handleStart(eventType);
    } else if (state == SagaState.RUNNING) {
      handleProgress(eventType);
    }
    // 其他状态（COMPLETED/FAILED/TERMINATED）不再处理事件
  }

  /**
   * 处理起始事件（由 Saga 子类实现具体编排逻辑）
   *
   * <p>在此方法中调用下游服务，任一步骤失败需调用 {@link #compensate(String)} 触发补偿。
   *
   * @param eventType 起始事件类型
   */
  protected abstract void handleStart(String eventType);

  /**
   * 处理编排推进事件
   *
   * <p>在 Saga RUNNING 状态下，处理编排过程中的后续事件（如"库存已扣减"→"支付完成"）。
   *
   * @param eventType 推进事件类型
   */
  protected void handleProgress(String eventType) {
    // 默认空实现，子类按需重写
  }

  /**
   * 触发补偿流程
   *
   * <p>当 Saga 编排步骤调用下游服务失败时调用此方法，设置状态为 COMPENSATING 并调用 {@link
   * #doCompensate()}。
   *
   * @param reason 补偿触发原因
   */
  protected void compensate(String reason) {
    this.state = SagaState.COMPENSATING;
    this.lastUpdatedAt = Instant.now();
    try {
      doCompensate();
      this.state = SagaState.COMPENSATED;
    } catch (Exception e) {
      this.state = SagaState.FAILED;
      throw e;
    }
  }

  /**
   * 执行补偿逻辑（由 Saga 子类实现）
   *
   * <p>补偿操作必须幂等：多次调用结果一致。
   */
  protected abstract void doCompensate();

  /**
   * 完成编排
   *
   * <p>所有步骤成功后调用此方法，将状态置为 COMPLETED。
   */
  protected void complete() {
    this.state = SagaState.COMPLETED;
    this.lastUpdatedAt = Instant.now();
  }

  /**
   * 获取当前 Saga 状态
   *
   * @return 当前状态
   */
  public SagaState getState() {
    return state;
  }

  /**
   * 设置 Saga 状态（供 SagaManager 持久化恢复使用）
   *
   * @param state 新状态
   */
  public void setState(SagaState state) {
    this.state = state;
    this.lastUpdatedAt = Instant.now();
  }

  /**
   * 获取关联值
   *
   * @return 关联值
   */
  public String getAssociationValue() {
    return associationValue;
  }

  /**
   * 设置关联值
   *
   * @param associationValue 关联值
   */
  protected void setAssociationValue(String associationValue) {
    this.associationValue = associationValue;
  }

  /**
   * 获取创建时间
   *
   * @return 创建时间
   */
  public Instant getCreatedAt() {
    return createdAt;
  }

  /**
   * 获取最后更新时间
   *
   * @return 最后更新时间
   */
  public Instant getLastUpdatedAt() {
    return lastUpdatedAt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    AbstractSaga that = (AbstractSaga) o;
    return Objects.equals(associationValue, that.associationValue)
        && Objects.equals(getAssociationKey(), that.getAssociationKey());
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(getAssociationKey(), associationValue);
  }
}
