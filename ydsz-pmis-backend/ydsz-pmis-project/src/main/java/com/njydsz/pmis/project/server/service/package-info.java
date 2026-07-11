/**
 * 业务服务接口层（Service Interface）。
 *
 * <p>本包定义项目模块全部业务能力的"接口契约"，实现类统一放在 {@code project.service.impl} 子包。
 * 业务编排、跨服务调用、事务控制、事件发布等逻辑均在 Service 实现类中完成。Service 命名
 * 规则：{@code {业务域}Service} + {@code {业务域}ServiceImpl}，接口与实现严格 1:1 对应。
 *
 * <h3>核心组件（按业务域）</h3>
 * <ul>
 *   <li><b>商机/立项</b>：OpportunityService、OpportunityFollowService、InitiationService</li>
 *   <li><b>合同</b>：ContractService、ContractChangeService、ContractSupplementService、ContractTemplateService</li>
 *   <li><b>执行/交付/工时</b>：WbsTaskService、TimeEntryService、DeliveryService、EvmMeasureService</li>
 *   <li><b>财务</b>：BudgetService（注：实际类为 BudgetItem 相关）、PaymentService、InvoiceService、ExpenseService、PurchaseService、RevenueService、CostAllocationService、ProfitService、ProfitSimulationService、RateCardService、RateInternalService、CustomerCreditService</li>
 *   <li><b>风险/变更/收尾</b>：RiskService、ProjectChangeService、ProjectClosureService、WarrantyService</li>
 *   <li><b>工单/满意度</b>：OpsTicketService、SatisfactionService</li>
 *   <li><b>规则引擎</b>：DecisionTableEvalService</li>
 *   <li><b>报表/驾驶舱</b>：AdvancedReportService、CockpitReportService、ReportService、ReportExportService、AsyncExportService、ImportService、BillableUtilizationService、SearchService</li>
 *   <li><b>对账/告警</b>：DailyReconcileService、ReconcileService、AlertDispatchService</li>
 * </ul>
 *
 * <h3>子包</h3>
 * <ul>
 *   <li>{@link com.njydsz.pmis.project.server.service.impl} - Service 实现类（统一后缀 {@code Impl}）</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>面向接口编程</b>：Controller 仅依赖 Service 接口，不直接引用 Impl，便于单测与替换</li>
 *   <li><b>事务边界</b>：{@code @Transactional} 标注在 Service 实现类的方法上（默认按异常回滚）</li>
 *   <li><b>粒度控制</b>：单个 Service 方法的事务粒度建议控制在 5 个 SQL 以内，避免长事务</li>
 *   <li><b>幂等声明</b>：对幂等性有要求的写入方法必须在 Javadoc 中显式声明</li>
 *   <li><b>结果统一</b>：Service 层允许抛 {@code BizException}，由全局异常处理器统一封装为 {@code Result}</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>Service 接口禁止使用 {@code default} 方法（避免不同实现对契约理解不一致）</li>
 *   <li>Service 不直接调用 HTTP API，必须经 Feign Client</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.project.server.service;
