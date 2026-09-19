package com.njydsz.common.event.saga;

/**
 * Saga 编排状态枚举（F-1）
 *
 * <p>借鉴 Axon Framework 的 Saga 生命周期模型（{@code @StartSaga} / {@code @EndSaga}），
 * 定义轻量级 Saga 实例的状态流转：
 *
 * <ul>
 *   <li>{@code CREATED} - 已创建（等待事件触发）
 *   <li>{@code RUNNING} - 执行中（已匹配起始事件，正在编排）
 *   <li>{@code COMPENSATING} - 补偿中（某步骤失败，触发补偿）
 *   <li>{@code COMPLETED} - 已完成（所有步骤成功执行）
 *   <li>{@code COMPENSATED} - 已补偿（回滚完成）
 *   <li>{@code FAILED} - 失败（不可恢复错误）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.19
 */
public enum SagaState {
  /** 已创建（等待起始事件触发） */
  CREATED,
  /** 执行中（已启动，编排步骤进行中） */
  RUNNING,
  /** 补偿中（部分步骤失败，执行补偿逻辑） */
  COMPENSATING,
  /** 已完成（所有步骤成功） */
  COMPLETED,
  /** 已补偿（补偿步骤成功完成） */
  COMPENSATED,
  /** 失败（不可恢复错误） */
  FAILED
}
