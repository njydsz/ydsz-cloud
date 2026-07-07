/**
 * 持久化对象（Data Object / Entity）层。
 *
 * <p>本包定义项目模块所有数据库表的实体映射（POJO），与 {@code pmis_*} 表结构一一对应。
 * 所有实体继承 {@link java.io.Serializable}，主键采用 MyBatis-Plus 雪花 ID 策略，并使用
 * 字段填充（{@code FieldFill.INSERT} / {@code FieldFill.INSERT_UPDATE}）自动写入
 * 创建人、创建时间、更新人、更新时间、租户 ID 等公共字段。
 *
 * <h3>核心组件（按业务域）</h3>
 * <ul>
 *   <li><b>商机/立项</b>：OpportunityDO、OpportunityFollowDO、InitiationDO、GateReviewDO</li>
 *   <li><b>合同</b>：ContractDO、ContractChangeDO、ContractSupplementDO、ContractTemplateDO</li>
 *   <li><b>执行/交付/工时</b>：WbsTaskDO、TimeEntryDO、DeliveryItemDO、DeliveryStandardDO、EvmMeasureDO</li>
 *   <li><b>财务</b>：BudgetItemDO、PaymentDO、InvoiceDO、ExpenseDO、PurchaseDO、RevenueDO、CostAllocationDO、ProfitSnapshotDO、ProfitSimulationDO、RateCardDO、RateInternalDO、CustomerCreditDO</li>
 *   <li><b>风险/变更/收尾</b>：RiskDO、ProjectChangeDO、ProjectClosureDO、WarrantyDO、OpsTicketDO、SatisfactionDO</li>
 *   <li><b>规则引擎</b>：RuleDefinitionDO、RuleVersionHistoryDO、RuleDependencyDO、RuleChainGraphDO、RuleDecisionTreeDO、RuleTestCaseDO、RuleExecutionTraceDO、RuleTemplateDO、RulePackDO、RulePackInstallDO、RuleABPolicyDO、RuleABRollbackDO、RuleCanaryBucketDO、RuleScorecardDO、RuleScriptDO、RuleVariableDefDO、DecisionTableDO</li>
 *   <li><b>对账/快照</b>：DailyReconcileDO、BillableUtilizationSnapshotDO、AlertDispatchDO</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>表名显式</b>：使用 {@code @TableName("pmis_xxx")} 显式指定，禁止依赖 MyBatis-Plus 默认下划线转换</li>
 *   <li><b>逻辑删除</b>：使用 {@code @TableLogic} 标记逻辑删除字段</li>
 *   <li><b>乐观锁</b>：高并发更新表使用 {@code @Version} 注解版本号字段</li>
 *   <li><b>租户隔离</b>：所有表必须包含 {@code tenant_id} 字段，配合 {@code DataScope} 拦截器自动注入</li>
 *   <li><b>自动填充</b>：{@code createBy} / {@code updateBy} / {@code createTime} / {@code updateTime} 一律使用 {@code MetaObjectHandler} 自动填充</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>DO 仅用于持久化层，对外接口严禁直接返回 DO，必须转换为 VO</li>
 *   <li>字段命名遵守 Java 驼峰，{@code @TableField} 仅在特殊场景（如关键字冲突）使用</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.project.entity;
