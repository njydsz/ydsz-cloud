/**
 * LiteRule 领域层，包含规则仓储接口、事件、枚举、值对象等.
 *
 * <p>本模块定义了 LiteRule 规则引擎子系统的核心领域模型与仓储接口契约，保持不依赖具体框架实现的纯净性。
 * 涵盖规则版本、审批记录、决策表、执行链、测试用例等核心聚合根，以及规则配置刷新事件、
 * 异常码枚举、各类值对象（VO）和数据传输对象（DTO）。</p>
 *
 * <p>领域模型主要构成：</p>
 * <ul>
 *   <li>仓储接口：{@code RuleVersionRepository}、{@code RuleExecutionTraceRepository}、
 *       {@code ApprovalRecordRepository}、{@code DecisionTableRepository}、
 *       {@code RuleTestCaseRepository} 等，定义持久化契约</li>
 *   <li>领域事件：{@code RuleConfigRefreshEvent} 在规则配置变更时发布，驱动热加载与分布式广播</li>
 *   <li>枚举：{@code RuleStatusEnum} 定义规则状态机；{@code LiteruleExceptionCode} 统一异常码</li>
 *   <li>值对象：包括 {@code RuleDefinitionVO}、{@code RuleVersionVO}、{@code RuleChainGraphVO}、
 *       {@code DecisionTableVO}、{@code CEPPatternVO}、{@code CEPHitVO} 等运维修复视图对象</li>
 * </ul>
 *
 * <h3>领域边界</h3>
 *
 * <ul>
 *   <li>本模块仅定义接口与模型，不依赖 Spring、MyBatis 等框架注解</li>
 *   <li>DTO（如 {@code RuleVersionSaveDTO}、{@code DecisionTablePostDTO}）用于跨层数据传输</li>
 *   <li>仓储接口由基础设施层实现，领域层不感知具体存储技术</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
package com.njydsz.literule.domain;
