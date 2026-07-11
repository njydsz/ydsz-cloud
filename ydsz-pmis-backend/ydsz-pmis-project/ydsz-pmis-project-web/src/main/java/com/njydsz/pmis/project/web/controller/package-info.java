/**
 * 项目业务模块 HTTP 接口层（REST Controller）。
 *
 * <p>本包负责项目域全部对外 REST API，包括商机、立项、合同、补充协议、合同变更、
 * 采购、付款、发票、报销、收入、成本、利润、风险、复盘、收尾、规则引擎、驾驶舱等
 * 30+ 控制器的入口定义。所有接口遵循统一响应封装 {@code com.njydsz.pmis.common.api.Result}。
 *
 * <h3>核心组件（按业务域分组）</h3>
 * <ul>
 *   <li><b>商机/立项</b>：OpportunityController、OpportunityFollowController、InitiationController</li>
 *   <li><b>合同域</b>：ContractController、ContractChangeController、ContractSupplementController、ContractTemplateController</li>
 *   <li><b>执行/工时</b>：WbsTaskController、TimeEntryController、DeliveryController</li>
 *   <li><b>财务域</b>：BudgetController、PaymentController、InvoiceController、ExpenseController、PurchaseController、RevenueController、ProfitController、ProfitSimulationController</li>
 *   <li><b>风险/收尾</b>：RiskController、ProjectChangeController、ProjectClosureController、WarrantyController</li>
 *   <li><b>规则引擎</b>：RuleAdminController、RuleVariableAdminController</li>
 *   <li><b>驾驶舱/报表</b>：CockpitReportController、AdvancedReportController、ReportController、ReportExportController、AsyncExportController、ImportExportController</li>
 *   <li><b>其他</b>：RateCardController、RateInternalController、CustomerCreditController、SatisfactionController、OpsTicketController、SearchController、BffAggregateController、EvmController、BillableUtilizationController、ReconcileController、DailyReconcileController、AlertDispatchController</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>薄 Controller</b>：Controller 只做参数解析、权限校验、调用 Service、返回结果</li>
 *   <li><b>统一响应</b>：所有方法返回 {@code Result<T>}，禁止直接返回 POJO</li>
 *   <li><b>Swagger 标注</b>：必须标注 {@code @Tag} / {@code @Operation} / {@code @Parameter}</li>
 *   <li><b>权限控制</b>：通过 {@code @PrePermission} 注解统一管控，前端无需关心权限细节</li>
 *   <li><b>幂等保障</b>：涉及提交的接口必须标注 {@code @Idempotent} 防止重复提交</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>URL 前缀统一使用 {@code /project/业务域}，避免与 common 接口冲突</li>
 *   <li>Controller 不持有业务状态，所有跨方法依赖通过构造器注入 Service</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.project.web.controller;
