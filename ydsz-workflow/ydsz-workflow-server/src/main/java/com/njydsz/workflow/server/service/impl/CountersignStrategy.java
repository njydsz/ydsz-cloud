package com.njydsz.workflow.server.service.impl;

import com.njydsz.workflow.domain.dto.FlowTaskOperateDTO;
import com.njydsz.workflow.domain.enums.FlowPerformType;
import com.njydsz.workflow.domain.vo.FlowRunTaskVO;

/**
 * 会签推进策略接口（Strategy Pattern）
 *
 * <p>从 {@code FlowTaskCompleteServiceImpl} 拆分出的<b>策略模式</b>抽象。
 * 不同的会签类型（{@code OR / PARALLEL}）有不同的「完成条件」和「完成后的清理动作」，
 * 本接口将二者下沉到具体策略类，主流程 {@code FlowTaskPassService} 通过
 * {@link CountersignStrategyFactory} 工厂按 {@link FlowPerformType} 选择策略。
 *
 * <p><b>架构动机：</b>
 *
 * <ul>
 *   <li>原单体实现 {@code FlowTaskCompleteServiceImpl} 中会签逻辑超过 600 行 （if-else 链覆盖 6+ 种会签模式）
 *   <li>使用策略模式后：每种会签模式独立成类，<b>单一职责</b>；新增模式仅需新增策略类， <b>开闭原则</b>（对扩展开放、对修改关闭）
 *   <li>主流程 {@code FlowTaskPassService} 仅依赖本接口和工厂类， <b>解耦</b>具体会签模式
 * </ul>
 *
 * <p><b>策略实现清单：</b>
 *
 * <ul>
 *   <li>{@link OrCountersignStrategy} — OR（或签 / 任一通过）
 *   <li>{@link ParallelCountersignStrategy} — PARALLEL（并行会签 / 全部通过）
 *   <li>{@link WeightedCountersignStrategy} — WEIGHTED（票签 / 加权投票，通过权重比例达标后推进）
 * </ul>
 *
 * <p><b>调用契约（顺序）：</b>
 *
 * <ol>
 *   <li>{@link #preCheck} — 预检查（可选重写，默认 no-op）
 *   <li>{@link #onUserPassed} — 累加计数 / 标记用户已处理（必实现）
 *   <li>{@link #shouldAdvance} — 决定是否满足推进条件（必实现）
 *   <li>{@link #onAdvance} — 推进前的清理（skipByNode 等，可选重写）
 *   <li>主流程推进 → 触发 onAdvanceAfter（事件 / 审计已在主流程统一处理）
 * </ol>
 *
 * <p><b>扩展指引：</b>
 *
 * <p>新增会签类型只需三步：
 *
 * <ol>
 *   <li>在 {@link FlowPerformType} 枚举中添加新值
 *   <li>实现本接口 + 标注 {@code @Component}
 *   <li>在 {@link CountersignStrategyFactory} 中注册新类型 → 策略映射
 * </ol>
 *
 * <b>无需修改主流程</b> {@code FlowTaskPassService}。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see FlowPerformType 会签类型枚举
 * @see CountersignStrategyFactory 策略工厂
 * @see FlowTaskPassService 主流程（策略调用方）
 * @see FlowRunTaskVO 运行时任务视图对象
 * @see FlowTaskOperateDTO 任务操作 DTO
 */
public interface CountersignStrategy {

  /**
   * 当前策略支持的会签类型。
   *
   * @return 当前策略支持的会签类型枚举
   */
  FlowPerformType supportedType();

  /**
   * 预检查：当前用户通过操作的前置校验。
   *
   * <p>默认无操作；子类可按需重写。
   *
   * @param task 当前运行时任务
   * @param dto 任务操作参数
   */
  default void preCheck(FlowRunTaskVO task, FlowTaskOperateDTO dto) {
    // 默认 no-op
  }

  /**
   * 当前用户已通过：累加 approveFinished 等计数。
   *
   * <p>主流程在调用本方法前已完成"完成当前 task 并归档"动作；本方法负责 更新会签维度的状态（如并行计数器）。
   *
   * @param task 当前运行时任务
   * @param dto 任务操作参数
   */
  void onUserPassed(FlowRunTaskVO task, FlowTaskOperateDTO dto);

  /**
   * 是否满足推进条件（达到完成阈值）。
   *
   * @param task 当前运行时任务
   * @return true 表示满足推进条件（达到完成阈值）
   */
  boolean shouldAdvance(FlowRunTaskVO task);

  /**
   * 推进前的清理动作（如 PARALLEL 完成后 skip 同节点其他 PENDING 任务）。
   *
   * @param task 当前运行时任务
   * @param dto 任务操作参数
   */
  default void onAdvance(FlowRunTaskVO task, FlowTaskOperateDTO dto) {
    // 默认 no-op
  }
}
