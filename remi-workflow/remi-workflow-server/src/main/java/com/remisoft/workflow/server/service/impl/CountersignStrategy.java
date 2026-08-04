package com.remisoft.workflow.server.service.impl;

import com.remisoft.workflow.domain.dto.FlowTaskOperateDTO;
import com.remisoft.workflow.domain.entity.FlowRunTask;
import com.remisoft.workflow.domain.enums.FlowPerformType;

/**
 * 会签推进策略接口（Strategy Pattern）
 *
 * <p>从 {@code FlowTaskCompleteServiceImpl} 拆分出的<b>策略模式</b>抽象。
 * 不同的会签类型（{@code OR / PARALLEL / SEQUENTIAL / VOTE / WEIGHTED_VOTE / FOREACH_PARALLEL}）
 * 有不同的「完成条件」和「完成后的清理动作」，本接口将二者下沉到具体策略类，
 * 主流程 {@code FlowTaskPassService} 通过 {@link CountersignStrategyFactory} 工厂
 * 按 {@link FlowPerformType} 选择策略。
 * 是大厂 B 端工作流「灵活会签模式扩展」的关键设计。
 *
 * <p><b>架构动机：</b>
 * <ul>
 *   <li>原单体实现 {@code FlowTaskCompleteServiceImpl} 中会签逻辑超过 600 行
 *       （if-else 链覆盖 6+ 种会签模式）</li>
 *   <li>使用策略模式后：每种会签模式独立成类，<b>单一职责</b>；新增模式仅需新增策略类，
 *       <b>开闭原则</b>（对扩展开放、对修改关闭）</li>
 *   <li>主流程 {@code FlowTaskPassService} 仅依赖本接口和工厂类，
 *       <b>解耦</b>具体会签模式</li>
 * </ul>
 *
 * <p><b>策略实现清单：</b>
 * <ul>
 *   <li>{@link OrCountersignStrategy} — OR（依次 / 任一通过）</li>
 *   <li>{@link ParallelCountersignStrategy} — PARALLEL（并行 / 全部通过）</li>
 *   <li>{@link SequentialCountersignStrategy} — SEQUENTIAL（依次 / 顺序审批）</li>
 *   <li>{@link VoteCountersignStrategy} — VOTE（投票 / 按比例）</li>
 *   <li>{@link WeightedVoteCountersignStrategy} — WEIGHTED_VOTE（加权投票）</li>
 *   <li>{@link ForeachCountersignStrategy} — FOREACH_PARALLEL（多元素并行）</li>
 * </ul>
 *
 * <p><b>调用契约（顺序）：</b>
 * <ol>
 *   <li>{@link #preCheck} — 预检查（可选重写，默认 no-op）</li>
 *   <li>{@link #onUserPassed} — 累加计数 / 标记用户已处理（必实现）</li>
 *   <li>{@link #shouldAdvance} — 决定是否满足推进条件（必实现）</li>
 *   <li>{@link #onAdvance} — 推进前的清理（skipByNode 等，可选重写）</li>
 *   <li>主流程推进 → 触发 onAdvanceAfter（事件 / 审计已在主流程统一处理）</li>
 * </ol>
 *
 * <p><b>扩展指引：</b>
 * <p>新增会签类型只需三步：
 * <ol>
 *   <li>在 {@link FlowPerformType} 枚举中添加新值</li>
 *   <li>实现本接口 + 标注 {@code @Component}</li>
 *   <li>在 {@link CountersignStrategyFactory} 中注册新类型 → 策略映射</li>
 * </ol>
 * <b>无需修改主流程</b> {@code FlowTaskPassService}。
 *
 * @author remi-team
 * @since 1.0.0
 *
 * @see FlowPerformType 会签类型枚举
 * @see CountersignStrategyFactory 策略工厂
 * @see FlowTaskPassService 主流程（策略调用方）
 * @see FlowRunTask 运行时任务实体
 * @see FlowTaskOperateDTO 任务操作 DTO
 */
public interface CountersignStrategy {

    /**
     * 当前策略支持的会签类型。
     */
    FlowPerformType supportedType();

    /**
     * 预检查：当前用户通过操作的前置校验。
     *
     * <p>默认无操作；SEQUENTIAL/FOREACH 等可能校验"当前用户是否本轮应处理的人"。
     */
    default void preCheck(FlowRunTask task, FlowTaskOperateDTO dto) {
        // 默认 no-op
    }

    /**
     * 当前用户已通过：累加 approveFinished 等计数。
     *
     * <p>主流程在调用本方法前已完成"完成当前 task 并归档"动作；本方法负责
     * 更新会签维度的状态（如并行计数器）。
     */
    void onUserPassed(FlowRunTask task, FlowTaskOperateDTO dto);

    /**
     * 是否满足推进条件（达到完成阈值）。
     */
    boolean shouldAdvance(FlowRunTask task);

    /**
     * 推进前的清理动作（如 PARALLEL 完成后 skip 同节点其他 PENDING 任务）。
     */
    default void onAdvance(FlowRunTask task, FlowTaskOperateDTO dto) {
        // 默认 no-op
    }
}
