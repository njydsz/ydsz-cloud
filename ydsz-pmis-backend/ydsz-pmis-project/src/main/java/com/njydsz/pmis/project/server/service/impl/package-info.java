/**
 * 业务服务实现层（Service Impl）。
 *
 * <p>本子包是 {@link com.njydsz.pmis.project.server.service} 包接口契约的具体实现，统一后缀
 * {@code Impl}，由 Spring 扫描并注册为 Bean。Service 实现是项目模块业务逻辑的主战场，
 * 承担：事务控制、跨服务调用、事件发布、权限/数据权限校验、业务编排等职责。
 *
 * <h3>核心组件（与同名 Service 接口一一对应）</h3>
 * <ul>
 *   <li>商机/立项：OpportunityServiceImpl、OpportunityFollowServiceImpl、InitiationServiceImpl</li>
 *   <li>合同：ContractServiceImpl、ContractChangeServiceImpl、ContractSupplementServiceImpl、ContractTemplateServiceImpl</li>
 *   <li>执行：WbsTaskServiceImpl、TimeEntryServiceImpl、DeliveryServiceImpl、EvmMeasureServiceImpl</li>
 *   <li>财务：PaymentServiceImpl、InvoiceServiceImpl、ExpenseServiceImpl、PurchaseServiceImpl、RevenueServiceImpl、CostAllocationServiceImpl、ProfitServiceImpl、ProfitSimulationServiceImpl、RateCardServiceImpl、RateInternalServiceImpl、CustomerCreditServiceImpl</li>
 *   <li>风险/变更/收尾：RiskServiceImpl、ProjectChangeServiceImpl、ProjectClosureServiceImpl、WarrantyServiceImpl</li>
 *   <li>工单/满意度：OpsTicketServiceImpl、SatisfactionServiceImpl</li>
 *   <li>规则引擎：DecisionTableEvalServiceImpl</li>
 *   <li>报表/驾驶舱：AdvancedReportServiceImpl、CockpitReportServiceImpl、ReportServiceImpl、ReportExportServiceImpl、AsyncExportServiceImpl、ImportServiceImpl、BillableUtilizationServiceImpl、SearchServiceImpl</li>
 *   <li>对账/告警：DailyReconcileServiceImpl、ReconcileServiceImpl、AlertDispatchServiceImpl</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>事务收敛</b>：{@code @Transactional(rollbackFor = Exception.class)} 显式声明，避免默认仅回滚 RuntimeException</li>
 *   <li><b>读 / 写分离</b>：纯读方法标注 {@code @Transactional(readOnly = true)}，走从库</li>
 *   <li><b>数据权限</b>：通过 {@code @DataScope} 注解自动注入数据权限过滤条件</li>
 *   <li><b>日志规范</b>：业务关键路径必须记录 INFO 日志（操作人、操作对象、变更前后值）</li>
 *   <li><b>异常统一</b>：业务校验失败抛 {@code BizException} 并附带明确的 {@code BizErrorCode}，禁止直接返回 null/boolean</li>
 *   <li><b>幂等保护</b>：写入操作需在 Mapper 层使用乐观锁或 DB 唯一约束确保幂等</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>Impl 内部禁止使用 {@code @Autowired} 字段注入，统一构造器注入（{@code @RequiredArgsConstructor}）</li>
 *   <li>事务方法内禁止调用同 Service 的其他事务方法（自调用失效），必要时通过 AOP 代理或拆分 Service</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.project.server.service.impl;
