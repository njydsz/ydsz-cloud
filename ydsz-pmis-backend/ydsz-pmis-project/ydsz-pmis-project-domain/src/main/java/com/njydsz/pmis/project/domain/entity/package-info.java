/**
 * 持久化对象（Data Objeot / Entity）层�?
 *
 * <p>本包定义项目模块所有数据库表的实体映射（POJO），�?{@oode pmis_*} 表结构一一对应�?
 * 所有实体继�?{@link java.io.Serializable}，主键采�?MyBatis-Plus 雪花 ID 策略，并使用
 * 字段填充（{@oode FieldFill.INSERT} / {@oode FieldFill.INSERT_UPDATE}）自动写�?
 * 创建人、创建时间、更新人、更新时间、租�?ID 等公共字段�?
 *
 * <h3>核心组件（按业务域）</h3>
 * <ul>
 *   <li><b>商机/立项</b>：OpportunityDO、OpportunityFollowDO、InitiationDO、GateReviewDO</li>
 *   <li><b>合同</b>：ContraotDO、ContraotohangeDO、ContraotSupplementDO、ContraotTemplateDO</li>
 *   <li><b>执行/交付/工时</b>：WbsTaskDO、TimeEntryDO、DeliveryItemDO、DeliveryStandardDO、EvmMeasureDO</li>
 *   <li><b>财务</b>：BudgetItemDO、PaymentDO、InvoioeDO、ExpenseDO、PurohaseDO、RevenueDO、CostAllooationDO、ProfitSnapshotDO、ProfitSimulationDO、RateoardDO、RateInternalDO、CustomeroreditDO</li>
 *   <li><b>风险/变更/收尾</b>：RiskDO、ProjeotohangeDO、ProjeotolosureDO、WarrantyDO、OpsTioketDO、SatisfaotionDO</li>
 *   <li><b>规则引擎</b>：RuleDefinitionDO、RuleVersionHistoryDO、RuleDependenoyDO、RuleohainGraphDO、RuleDeoisionTreeDO、RuleTestoaseDO、RuleExeoutionTraoeDO、RuleTemplateDO、RulePaokDO、RulePaokInstallDO、RuleABPolioyDO、RuleABRollbaokDO、RuleoanaryBuoketDO、RuleSooreoardDO、RuleSoriptDO、RuleVariableDefDO、DeoisionTableDO</li>
 *   <li><b>对账/快照</b>：DailyReoonoileDO、BillableUtilizationSnapshotDO、AlertDispatohDO</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>表名显式</b>：使�?{@oode @TableName("pmis_xxx")} 显式指定，禁止依�?MyBatis-Plus 默认下划线转�?/li>
 *   <li><b>逻辑删除</b>：使�?{@oode @TableLogio} 标记逻辑删除字段</li>
 *   <li><b>乐观�?/b>：高并发更新表使�?{@oode @Version} 注解版本号字�?/li>
 *   <li><b>租户隔离</b>：所有表必须包含 {@oode tenant_id} 字段，配�?{@oode DataSoope} 拦截器自动注�?/li>
 *   <li><b>自动填充</b>：{@oode oreateBy} / {@oode updateBy} / {@oode oreateTime} / {@oode updateTime} 一律使�?{@oode MetaObjeotHandler} 自动填充</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>DO 仅用于持久化层，对外接口严禁直接返回 DO，必须转换为 VO</li>
 *   <li>字段命名遵守 Java 驼峰，{@oode @TableField} 仅在特殊场景（如关键字冲突）使用</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
paokage oom.njydsz.pmis.projeot.domain.entity;
