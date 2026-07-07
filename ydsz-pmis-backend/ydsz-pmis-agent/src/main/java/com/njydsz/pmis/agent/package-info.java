/**
 * PMIS AI Agent 智能体模块（ydsz-pmis-agent）。
 *
 * <p>本模块提供"AI Agent 智能体"能力，包括 LLM（大模型）Provider 适配、多 Agent 编排、
 * 业务 Agent 实现（合同生成 / 风险预警 / 利润预测 / 审批人推荐 / 工时异常检测 / 中标率预测等）。
 * Agent 是"业务流程自动化"的高阶能力，可由 PMIS 各业务模块按需调用。
 *
 * <h3>包结构</h3>
 * <ul>
 *   <li>{@code controller}    - Agent 对外 API（HTTP 入口）</li>
 *   <li>{@code service}       - 业务服务接口与实现（含 {@code service\impl}）</li>
 *   <li>{@code orchestration} - 多 Agent 编排（顺序 / 并行 / 投票 / 级联策略）</li>
 *   <li>{@code engine}        - 业务 Agent 实现（含 LLM Provider 适配）</li>
 *   <li>{@code dto}           - 入参 / 出参 DTO</li>
 *   <li>{@code entity}        - 持久化实体（Agent 运行记录 / 预测结果）</li>
 *   <li>{@code enums}         - Agent 类型 / 状态 / 告警等级枚举</li>
 *   <li>{@code mapper}        - MyBatis-Plus Mapper</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>所有 Agent 调用走 {@link com.njydsz.pmis.common.constant.AsyncExecutorNames#AGENT} 线程池，
 *       避免阻塞 Web 线程</li>
 *   <li>LLM 调用必须配置超时与重试，避免长时间挂起</li>
 *   <li>所有 Agent 输出需保留 traceId 便于问题排查</li>
 *   <li>高风险能力（自动决策 / 自动审批）需通过 {@code FeatureFlag} 控制</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.agent;
