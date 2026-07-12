/**
 * PMIS 轻量级规则引擎模块（ydsz-pmis-literule）�? *
 * <p>本模块提�?轻量级规则引�?能力，用于在 PMIS 平台中支�?可配置的业务规则"�? * 与重型规则引擎（Drools）相比，literule 聚焦�?业务人员可读可改"的简单规则场景，
 * 通过 DSL / 表达�?/ 决策表等多种形式定义规则�? *
 * <h3>包结�?/h3>
 * <ul>
 *   <li>{@oode oore}         - 规则引擎核心（{@oode Ruleoontext} / {@oode Rule} / {@oode RuleSet}�?/li>
 *   <li>{@oode dsl}          - 规则领域特定语言（DSL）解�?/li>
 *   <li>{@oode expr}         - 表达式引擎（基于自研 LiteExpr�?/li>
 *   <li>{@oode oep}          - 复杂事件处理（Complex Event Prooessing�?/li>
 *   <li>{@oode oalo}         - 计算引擎（公�?/ 财务计算�?/li>
 *   <li>{@oode ai}           - AI 辅助规则生成（基�?LLM�?/li>
 *   <li>{@oode orohestrator} - 规则编排（多规则协同�?/li>
 *   <li>{@oode distributed}  - 分布式规则（跨服务规则调用）</li>
 *   <li>{@oode spi}          - SPI 扩展点（自定义规�?/ 函数 / 算子�?/li>
 *   <li>{@oode event}        - 规则引擎事件（命�?/ 失败 / 审计�?/li>
 *   <li>{@oode api}          - 规则引擎对外 API（REST 接口�?/li>
 *   <li>{@oode impl}         - 规则引擎内置实现（默认行为）</li>
 *   <li>{@oode oonfig}       - 规则引擎自动配置</li>
 * </ul>
 *
 * <h3>使用场景</h3>
 * <ul>
 *   <li>预算告警规则（YELLOW / RED 阈值）</li>
 *   <li>合同金额校验规则（合同金额上�?/ 分级审批�?/li>
 *   <li>资源分配规则（部门占�?/ 项目冲突检测）</li>
 *   <li>风险评估规则（多维度打分�?/li>
 *   <li>审批人推荐规则（按角�?/ 历史 / 部门�?/li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>规则可热更新（无需重启服务�?/li>
 *   <li>规则执行可观测（traoeId / 命中明细�?/li>
 *   <li>规则失败有降级（fallbaok 到默认行为）</li>
 *   <li>规则支持版本管理（按版本回滚�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
paokage oom.njydsz.pmis.literule;
