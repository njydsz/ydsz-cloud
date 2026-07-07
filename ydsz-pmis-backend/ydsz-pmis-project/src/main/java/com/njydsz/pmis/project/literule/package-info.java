/**
 * 轻量规则引擎（LiteRule）业务适配层。
 *
 * <p>本包是项目模块对 {@code com.njydsz.pmis.literule}（通用规则引擎）的"业务侧适配层"，
 * 提供规则配置中心、变量注册、版本管理、模板、A/B 实验、金丝雀、依赖图、决策表、
 * 冲突检测、AI 生成等业务能力。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@link com.njydsz.pmis.project.literule.RuleConfigProviderImpl} - 规则配置提供者（SPI 实现）</li>
 *   <li>{@link com.njydsz.pmis.project.literule.RuleVersionRepositoryImpl} - 规则版本仓库</li>
 *   <li>{@link com.njydsz.pmis.project.literule.DecisionTableConfigProviderImpl} - 决策表配置提供者</li>
 *   <li>{@link com.njydsz.pmis.project.literule.BudgetSnapshotProviderImpl} - 预算快照提供者（SPI 实现）</li>
 *   <li>{@link com.njydsz.pmis.project.literule.ReconcileDataProviderImpl} - 对账数据提供者（SPI 实现）</li>
 *   <li>{@link com.njydsz.pmis.project.literule.DatabaseVariableRegistry} - 数据库变量注册中心</li>
 *   <li>{@link com.njydsz.pmis.project.literule.ThresholdProviderBridge} - 阈值桥接器</li>
 *   <li>{@link com.njydsz.pmis.project.literule.DbTraceRecorder} - 规则执行轨迹 DB 落地器</li>
 *   <li>{@link com.njydsz.pmis.project.literule.RuleCategoryTreeService} - 规则分类树服务</li>
 *   <li>{@link com.njydsz.pmis.project.literule.RuleTemplateService} - 规则模板服务</li>
 *   <li>{@link com.njydsz.pmis.project.literule.RuleGenerationService} - AI 辅助规则生成（NL2Rule）</li>
 *   <li>{@link com.njydsz.pmis.project.literule.RuleDependencyService} - 规则依赖关系服务</li>
 *   <li>{@link com.njydsz.pmis.project.literule.RuleChainGraphService} - 规则责任链图服务</li>
 *   <li>{@link com.njydsz.pmis.project.literule.RuleConflictDetector} - 规则冲突检测器</li>
 *   <li>{@link com.njydsz.pmis.project.literule.RulePackService} - 规则包管理（打包/安装/版本化）</li>
 *   <li>{@link com.njydsz.pmis.project.literule.ABTestAutoRollbackService} - A/B 实验自动回滚</li>
 *   <li>{@link com.njydsz.pmis.project.literule.ABTestNotifier} - A/B 实验变更通知</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>通用与业务分离</b>：{@code literule} 通用能力放 {@code common.literule}，本包只做业务实现</li>
 *   <li><b>多租户隔离</b>：所有规则查询 / 写入必须带 {@code tenantId}，避免跨租户串数据</li>
 *   <li><b>版本化</b>：规则发布必须生成版本，支持回滚、灰度、金丝雀</li>
 *   <li><b>可灰度</b>：核心规则上线必须经 A/B 或金丝雀验证</li>
 *   <li><b>可观测</b>：规则执行轨迹落库，支持事后审计与回放</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>禁止直接调用 {@code BaseMapper} 操作 {@code pmis_rule_*} 表，必须经本包 Service</li>
 *   <li>AI 生成规则发布前必须经过 {@code ExpressionValidationService} 校验与 {@code dryRun} 试跑</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.project.literule;
