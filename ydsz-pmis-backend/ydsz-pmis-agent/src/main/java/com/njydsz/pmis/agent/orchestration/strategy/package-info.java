/**
 * 编排策略实现子包。
 *
 * <p>实现 {@code OrchestrationStrategy} 接口的具体策略：
 * <ul>
 *   <li>{@code SequentialStrategy} - 顺序策略</li>
 *   <li>{@code ParallelStrategy}   - 并行策略</li>
 *   <li>{@code VotingStrategy}     - 投票策略</li>
 *   <li>{@code CascadeStrategy}    - 级联策略</li>
 * </ul>
 *
 * <p>每种策略作为独立 Bean 注册，通过 {@code OrchestrationMode} 枚举值路由。
 * 新增策略只需实现 {@code OrchestrationStrategy} 接口并在 Router 注册，无需修改编排引擎。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.agent.orchestration.strategy;
