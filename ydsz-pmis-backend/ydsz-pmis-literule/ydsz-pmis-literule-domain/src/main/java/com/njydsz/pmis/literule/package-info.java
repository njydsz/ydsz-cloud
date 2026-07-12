/**
 * PMIS 轻量级规则引擎模块（ydsz-pmis-literule）。
 *
 * <p>本模块提供"轻量级规则引擎"能力，用于在 PMIS 平台中支持"可配置的业务规则"。
 * 与重型规则引擎（Drools）相比，literule 聚焦于"业务人员可读可改"的简单规则场景，
 * 通过 DSL / 表达式 / 决策表等多种形式定义规则。
 *
 * <h3>包结构</h3>
 * <ul>
 *   <li>{@code core}         - 规则引擎核心（{@code RuleContext} / {@code Rule} / {@code RuleSet}）</li>
 *   <li>{@code dsl}          - 规则领域特定语言（DSL）解析</li>
 *   <li>{@code expr}         - 表达式引擎（基于自研 LiteExpr）</li>
 *   <li>{@code cep}          - 复杂事件处理（Complex Event Processing）</li>
 *   <li>{@code calc}         - 计算引擎（公式 / 财务计算）</li>
 *   <li>{@code ai}           - AI 辅助规则生成（基于 LLM）</li>
 *   <li>{@code orchestrator} - 规则编排（多规则协同）</li>
 *   <li>{@code distributed}  - 分布式规则（跨服务规则调用）</li>
 *   <li>{@code spi}          - SPI 扩展点（自定义规则 / 函数 / 算子）</li>
 *   <li>{@code event}        - 规则引擎事件（命中 / 失败 / 审计）</li>
 *   <li>{@code api}          - 规则引擎对外 API（REST 接口）</li>
 *   <li>{@code impl}         - 规则引擎内置实现（默认行为）</li>
 *   <li>{@code config}       - 规则引擎自动配置</li>
 * </ul>
 *
 * <h3>使用场景</h3>
 * <ul>
 *   <li>预算告警规则（YELLOW / RED 阈值）</li>
 *   <li>合同金额校验规则（合同金额上限 / 分级审批）</li>
 *   <li>资源分配规则（部门占比 / 项目冲突检测）</li>
 *   <li>风险评估规则（多维度打分）</li>
 *   <li>审批人推荐规则（按角色 / 历史 / 部门）</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>规则可热更新（无需重启服务）</li>
 *   <li>规则执行可观测（traceId / 命中明细）</li>
 *   <li>规则失败有降级（fallback 到默认行为）</li>
 *   <li>规则支持版本管理（按版本回滚）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.literule;
