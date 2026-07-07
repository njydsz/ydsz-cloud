/**
 * 项目业务计算与决策引擎层（Engine）。
 *
 * <p>本包集中放置与具体业务表解耦的"纯计算"或"决策判定"组件，是项目模块规则、算法、风控
 * 逻辑的沉淀层。Engine 内部无状态（除显式声明外），由 Service 层组合调用。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@link com.njydsz.pmis.project.engine.BudgetGuard} - 预算强管控校验（采购/费用）</li>
 *   <li>{@link com.njydsz.pmis.project.engine.EvmCalculator} - EVM 挣值分析（CV/SV/CPI/SPI/EAC/VAC/TCPI）</li>
 *   <li>{@link com.njydsz.pmis.project.engine.ProfitCalculator} - 利润核算（毛利率/EAC/人均产值/健康度）</li>
 *   <li>{@link com.njydsz.pmis.project.engine.SlaCalculator} - SLA 违约判定</li>
 *   <li>{@link com.njydsz.pmis.project.engine.StageGateValidator} - 项目阶段门径校验</li>
 *   <li>{@link com.njydsz.pmis.project.engine.ClosureAdmissionValidator} - 项目收尾准入校验</li>
 *   <li>{@link com.njydsz.pmis.project.engine.ChangeImpactEvaluator} - 项目变更影响评估</li>
 *   <li>{@link com.njydsz.pmis.project.engine.ContractRiskEvaluator} - 合同风险评估</li>
 *   <li>{@link com.njydsz.pmis.project.engine.RiskScoreEvaluator} - 风险评分计算</li>
 *   <li>{@link com.njydsz.pmis.project.engine.WinRateEvaluator} - 商机赢率评估</li>
 *   <li>{@link com.njydsz.pmis.project.engine.TimeEntryValidator} - 工时录入校验</li>
 *   <li>{@link com.njydsz.pmis.project.engine.DecisionTableEvaluator} - 决策表求值</li>
 *   <li>{@link com.njydsz.pmis.project.engine.ReconcileHandler} - 对账处理器</li>
 *   <li>{@link com.njydsz.pmis.project.engine.ReconcileResult} - 对账结果</li>
 *   <li>{@link com.njydsz.pmis.project.engine.ReconcileReport} - 对账报告</li>
 *   <li>{@link com.njydsz.pmis.project.engine.BudgetAlertEvent} - 预算告警事件</li>
 *   <li>{@link com.njydsz.pmis.project.engine.BudgetAlertEventListener} - 预算告警事件监听</li>
 *   <li>{@link com.njydsz.pmis.project.engine.AfterSalesCodeGen} - 售后单据号生成器</li>
 *   <li>{@link com.njydsz.pmis.project.engine.AlertCodeGen} - 告警编码生成器</li>
 * </ul>
 *
 * <h3>子包</h3>
 * <ul>
 *   <li>{@link com.njydsz.pmis.project.engine.alert} - 驾驶舱预警规则引擎及具体规则实现</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>无状态</b>：Engine 内部不持有可变状态，便于并发与单测</li>
 *   <li><b>纯函数优先</b>：输入参数确定时输出必须确定，不依赖全局变量</li>
 *   <li><b>可独立单测</b>：Engine 必须能在不启动 Spring 的情况下完成单测</li>
 *   <li><b>不持久化</b>：Engine 不直接调用 Mapper，IO 由 Service 编排</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>禁止在 Engine 中注入 {@code ApplicationContext} / {@code BeanFactory}</li>
 *   <li>新增 Engine 必须配套至少 1 个 JUnit 单测，覆盖正常 / 边界 / 异常分支</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.project.engine;
