/**
 * 数据访问层（MyBatis-Plus Mapper）。
 *
 * <p>本包定义项目模块所有表的 MyBatis-Plus Mapper 接口，统一继承 {@code BaseMapper<DO>}，
 * 提供 CRUD 能力 + 复杂 SQL 扩展。Mapper 命名规则：{@code {EntityName}Mapper}，
 * 与 {@code entity} 包下的 DO 一一对应。
 *
 * <h3>核心组件（按业务域）</h3>
 * <ul>
 *   <li><b>商机/立项</b>：OpportunityMapper、OpportunityFollowMapper、InitiationMapper、GateReviewMapper</li>
 *   <li><b>合同</b>：ContractMapper、ContractChangeMapper、ContractSupplementMapper、ContractTemplateMapper</li>
 *   <li><b>执行/交付/工时</b>：WbsTaskMapper、TimeEntryMapper、DeliveryItemMapper、DeliveryStandardMapper、EvmMeasureMapper</li>
 *   <li><b>财务</b>：BudgetItemMapper、PaymentMapper、InvoiceMapper、ExpenseMapper、PurchaseMapper、RevenueMapper、CostAllocationMapper、ProfitSnapshotMapper、ProfitSimulationMapper、RateCardMapper、RateInternalMapper、CustomerCreditMapper</li>
 *   <li><b>风险/变更/收尾</b>：RiskMapper、ProjectChangeMapper、ProjectClosureMapper、WarrantyMapper、OpsTicketMapper、SatisfactionMapper</li>
 *   <li><b>规则引擎</b>：RuleDefinitionMapper、RuleVersionHistoryMapper、RuleDependencyMapper、RuleChainGraphMapper、RuleDefinitionMapper、RuleTestCaseMapper、RuleExecutionTraceMapper、RuleTemplateMapper、RulePackMapper、RulePackInstallMapper、RuleABPolicyMapper、RuleABRollbackMapper、RuleCanaryBucketMapper、RuleVariableDefMapper、DecisionTableMapper</li>
 *   <li><b>对账/快照/告警</b>：DailyReconcileMapper、BillableUtilizationSnapshotMapper、AlertDispatchMapper</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>方法名优先</b>：简单 CRUD 使用 MyBatis-Plus 链式调用，禁止手写 XML 重复实现</li>
 *   <li><b>复杂 SQL 集中</b>：多表关联 / 复杂统计统一写在 {@code resources/mapper/project/} 下的 XML</li>
 *   <li><b>租户隔离</b>：所有自定义 SQL 必须带 {@code tenant_id} 过滤，由 {@code DataScope} 拦截器自动注入</li>
 *   <li><b>参数化</b>：禁止字符串拼接 SQL，统一使用 {@code @Param} 注解</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>Mapper 接口只放数据访问方法，不放业务逻辑</li>
 *   <li>Mapper 中的方法禁止返回 Map 类型（强类型 VO 优先），避免运行时类型错误</li>
 *   <li>批量操作必须使用 {@code @Insert(... batch=true)} 或 {@code saveBatch}，禁止在循环中单条插入</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.project.infra.mapper;
