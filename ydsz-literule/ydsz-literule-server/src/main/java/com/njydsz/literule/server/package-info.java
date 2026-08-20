/**
 * LiteRule 规则引擎核心服务层，提供规则评估、规则生命周期管理、DSL 解析、图执行、版本管理、审批流程等能力.
 *
 * <p>本模块是 LiteRule 规则引擎子系统的核心服务实现层，承载规则的全生命周期管理。从规则 DSL 的解析编译、
 * 版本快照与 AB 策略管理，到运行时评估引擎、图执行编排、CEP 复杂事件处理，以及规则热加载、分布式配置广播、
 * 压力测试、表达式预览与校验等运维能力，形成完整的规则引擎服务体系。</p>
 *
 * <p>核心能力分层：</p>
 * <ul>
 *   <li>规则评估：{@code DefaultRuleEngine} 提供统一评估入口；{@code ParallelRuleEvaluator} 支持并行评估；
 *       {@code ShardAwareRuleEngine} 实现分片感知的分布式评估</li>
 *   <li>生命周期管理：{@code RuleLifecycleService} 覆盖规则的创建、发布、下线、归档全流程，
 *       集成 {@code RuleApprovalWorkflowBridge} 审批工作流</li>
 *   <li>DSL 解析：{@code RuleDslParser} 解析规则 DSL 为内部模型，{@code RuleDslConverter} 支持多格式转换导出</li>
 *   <li>图编排：{@code RuleChain} / {@code RuleChainGraph} 构建规则链有向图，
 *       {@code DefaultGraphExecutionProvider} 驱动图节点按拓扑顺序执行</li>
 *   <li>CEP 处理：{@code CEPEngine} 基于事件流进行模式匹配，输出 {@code CEPHit} 命中结果</li>
 *   <li>表达式引擎：{@code LiteExprEngine} / {@code AviatorExpressionEngine} 双引擎并存，
 *       分别用于沙箱安全执行与高性能脚本求值</li>
 * </ul>
 *
 * <h3>关键组件</h3>
 *
 * <ul>
 *   <li>{@code DefaultRuleEngine} -- 默认规则引擎实现，支持缓存、熔断、超时控制</li>
 *   <li>{@code RuleLifecycleService} -- 规则生命周期服务，驱动状态流转</li>
 *   <li>{@code RuleTestRunner} -- 规则测试运行器，执行单测与回归用例</li>
 *   <li>{@code RuleDslParser} -- 规则 DSL 解析器</li>
 *   <li>{@code RuleChain} / {@code RuleChainGraph} -- 规则链与图执行模型</li>
 *   <li>{@code ShardAwareRuleEngine} -- 分片感知的分布式规则引擎</li>
 *   <li>{@code CEPEngine} -- 复杂事件处理引擎</li>
 *   <li>{@code RuleHotReloader} -- 规则热加载器，监听配置变更事件触发即时生效</li>
 *   <li>{@code LiteRuleSdk} / {@code LiteRuleSdkBuilder} -- SDK 入口，便于第三方接入</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
package com.njydsz.literule.server;
