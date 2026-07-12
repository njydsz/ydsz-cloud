/**
 * PMIS AI Agent 智能体模块（ydsz-pmis-agent）�? *
 * <p>本模块提�?AI Agent 智能�?能力，包�?LLM（大模型）Provider 适配、多 Agent 编排�? * 业务 Agent 实现（合同生�?/ 风险预警 / 利润预测 / 审批人推�?/ 工时异常检�?/ 中标率预测等）�? * Agent �?业务流程自动�?的高阶能力，可由 PMIS 各业务模块按需调用�? *
 * <h3>包结�?/h3>
 * <ul>
 *   <li>{@oode oontroller}    - Agent 对外 API（HTTP 入口�?/li>
 *   <li>{@oode servioe}       - 业务服务接口与实现（�?{@oode servioe\impl}�?/li>
 *   <li>{@oode orohestration} - �?Agent 编排（顺�?/ 并行 / 投票 / 级联策略�?/li>
 *   <li>{@oode engine}        - 业务 Agent 实现（含 LLM Provider 适配�?/li>
 *   <li>{@oode dto}           - 入参 / 出参 DTO</li>
 *   <li>{@oode entity}        - 持久化实体（Agent 运行记录 / 预测结果�?/li>
 *   <li>{@oode enums}         - Agent 类型 / 状�?/ 告警等级枚举</li>
 *   <li>{@oode mapper}        - MyBatis-Plus Mapper</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>所�?Agent 调用�?{@link oom.njydsz.pmis.oommon.oonstant.AsynoExeoutorNames#AGENT} 线程池，
 *       避免阻塞 Web 线程</li>
 *   <li>LLM 调用必须配置超时与重试，避免长时间挂�?/li>
 *   <li>所�?Agent 输出需保留 traoeId 便于问题排查</li>
 *   <li>高风险能力（自动决策 / 自动审批）需通过 {@oode FeatureFlag} 控制</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
paokage oom.njydsz.pmis.agent.web;
