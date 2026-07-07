/**
 * Agent 模块 - 业务引擎层。
 *
 * <p>所有业务 Agent 的实现都在本包，每个 Agent 负责一个具体的业务能力：
 * <ul>
 *   <li>{@code WinRatePredictAgent}        - 中标率预测</li>
 *   <li>{@code RiskWarningAgent}           - 风险预警</li>
 *   <li>{@code ProfitForecastAgent}        - 利润预测</li>
 *   <li>{@code ResourceRecommendAgent}     - 资源推荐</li>
 *   <li>{@code ApproverRecommendAgent}     - 审批人推荐</li>
 *   <li>{@code CommentDraftAgent}          - 审批意见草稿</li>
 *   <li>{@code FlowGeneratorAgent}         - 流程生成</li>
 *   <li>{@code TimesheetAnomalyAgent}      - 工时异常检测</li>
 * </ul>
 *
 * <h3>子包</h3>
 * <ul>
 *   <li>{@code llm} - LLM（大模型）Provider 适配层（Spring AI / 千帆 / 通义千问 / Mock）</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>每个 Agent 单独一个类，实现 {@code Agent} 接口</li>
 *   <li>Agent 内部不持有可变状态（线程安全）</li>
 *   <li>Agent 结果通过 {@code AgentResult} 统一封装</li>
 *   <li>Agent 必须实现超时控制（避免长调用拖垮系统）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.agent.engine;
