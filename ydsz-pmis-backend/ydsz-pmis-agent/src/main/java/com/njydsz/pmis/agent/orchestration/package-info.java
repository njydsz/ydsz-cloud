/**
 * Agent 模块 - 多 Agent 编排层。
 *
 * <p>支持"多个 Agent 协同工作"的能力，提供四种编排策略：
 * <ul>
 *   <li>{@code SequentialStrategy} - 顺序执行（前一个 Agent 输出作为后一个输入）</li>
 *   <li>{@code ParallelStrategy}   - 并行执行（多个 Agent 同时跑，取最快 / 全部结果）</li>
 *   <li>{@code VotingStrategy}     - 投票策略（多个 Agent 投票决定最终结果）</li>
 *   <li>{@code CascadeStrategy}    - 级联策略（前一个 Agent 决定是否调用下一个）</li>
 * </ul>
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@code OrchestrationRequest}  - 编排请求</li>
 *   <li>{@code OrchestrationResult}   - 编排结果</li>
 *   <li>{@code OrchestrationMode}     - 编排模式枚举</li>
 *   <li>{@code AgentBlackboard}      - 黑板模式（共享状态）</li>
 *   <li>{@code AgentMessage}         - Agent 间消息</li>
 *   <li>{@code AgentCoordinator}      - 协调器接口</li>
 *   <li>{@code AgentCoordinatorImpl}  - 协调器实现</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>编排策略实现 {@code OrchestrationStrategy} 接口</li>
 *   <li>编排超时时间由 {@code OrchestrationRequest} 显式控制</li>
 *   <li>编排结果包含每个 Agent 的执行细节，便于排查</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.agent.orchestration;
