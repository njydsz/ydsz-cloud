package com.njydsz.pmis.workflow.service.impl.strategy;

import com.njydsz.pmis.workflow.dto.FlowTaskOperateDTO;
import com.njydsz.pmis.workflow.entity.FlowRunTaskDO;
import com.njydsz.pmis.workflow.enums.FlowPerformType;

/**
 * 会签推进策略接口
 *
 * <p>从 {@code FlowTaskCompleteServiceImpl} 拆分出的策略模式抽象。
 * 不同的会签类型（OR/PARALLEL/SEQUENTIAL/VOTE/WEIGHTED_VOTE/FOREACH_PARALLEL）有
 * 不同的通过逻辑，本接口将"完成条件判断"和"完成后的清理动作"下沉到具体策略类，
 * 主流程 {@code FlowTaskPassService} 通过工厂按 {@link FlowPerformType} 选策略。
 *
 * <p>策略实现位于同包，命名规则 {@code <Type>CountersignStrategy}。
 * 新增会签类型只需：1) 添加枚举值；2) 实现本接口并标注 {@code @Component}；
 * 3) 在工厂注册。无需修改主流程。
 *
 * <p>调用契约（顺序）：
 * <ol>
 *   <li>{@link #preCheck(FlowRunTaskDO, FlowTaskOperateDTO)} — 预检查（可选重写）</li>
 *   <li>{@link #onUserPassed(FlowRunTaskDO, FlowTaskOperateDTO)} — 累加计数/标记用户已处理</li>
 *   <li>{@link #shouldAdvance(FlowRunTaskDO)} — 决定是否满足推进条件</li>
 *   <li>{@link #onAdvance(FlowRunTaskDO, FlowTaskOperateDTO)} — 推进前的清理（skipByNode 等）</li>
 *   <li>主流程推进 → 触发 onAdvanceAfter（事件/审计已在主流程统一处理）</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @since 1.7.0
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
    default void preCheck(FlowRunTaskDO task, FlowTaskOperateDTO dto) {
        // 默认 no-op
    }

    /**
     * 当前用户已通过：累加 approveFinished 等计数。
     *
     * <p>主流程在调用本方法前已完成"完成当前 task 并归档"动作；本方法负责
     * 更新会签维度的状态（如并行计数器）。
     */
    void onUserPassed(FlowRunTaskDO task, FlowTaskOperateDTO dto);

    /**
     * 是否满足推进条件（达到完成阈值）。
     */
    boolean shouldAdvance(FlowRunTaskDO task);

    /**
     * 推进前的清理动作（如 PARALLEL 完成后 skip 同节点其他 PENDING 任务）。
     */
    default void onAdvance(FlowRunTaskDO task, FlowTaskOperateDTO dto) {
        // 默认 no-op
    }
}
