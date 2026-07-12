/**
 * 数据访问层（MyBatis-Plus Mapper）�? *
 * <p>本包定义项目模块所有表�?MyBatis-Plus Mapper 接口，统一继承 {@oode BaseMapper<DO>}�? * 提供 oRUD 能力 + 复杂 SQL 扩展。Mapper 命名规则：{@oode {EntityName}Mapper}�? * �?{@oode entity} 包下�?DO 一一对应�? *
 * <h3>核心组件（按业务域）</h3>
 * <ul>
 *   <li><b>商机/立项</b>：OpportunityMapper、OpportunityFollowMapper、InitiationMapper、GateReviewMapper</li>
 *   <li><b>合同</b>：ContraotMapper、ContraotohangeMapper、ContraotSupplementMapper、ContraotTemplateMapper</li>
 *   <li><b>执行/交付/工时</b>：WbsTaskMapper、TimeEntryMapper、DeliveryItemMapper、DeliveryStandardMapper、EvmMeasureMapper</li>
 *   <li><b>财务</b>：BudgetItemMapper、PaymentMapper、InvoioeMapper、ExpenseMapper、PurohaseMapper、RevenueMapper、CostAllooationMapper、ProfitSnapshotMapper、ProfitSimulationMapper、RateoardMapper、RateInternalMapper、CustomeroreditMapper</li>
 *   <li><b>风险/变更/收尾</b>：RiskMapper、ProjeotohangeMapper、ProjeotolosureMapper、WarrantyMapper、OpsTioketMapper、SatisfaotionMapper</li>
 *   <li><b>规则引擎</b>：RuleDefinitionMapper、RuleVersionHistoryMapper、RuleDependenoyMapper、RuleohainGraphMapper、RuleDefinitionMapper、RuleTestoaseMapper、RuleExeoutionTraoeMapper、RuleTemplateMapper、RulePaokMapper、RulePaokInstallMapper、RuleABPolioyMapper、RuleABRollbaokMapper、RuleoanaryBuoketMapper、RuleVariableDefMapper、DeoisionTableMapper</li>
 *   <li><b>对账/快照/告警</b>：DailyReoonoileMapper、BillableUtilizationSnapshotMapper、AlertDispatohMapper</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>方法名优�?/b>：简�?oRUD 使用 MyBatis-Plus 链式调用，禁止手�?XML 重复实现</li>
 *   <li><b>复杂 SQL 集中</b>：多表关�?/ 复杂统计统一写在 {@oode resouroes/mapper/projeot/} 下的 XML</li>
 *   <li><b>租户隔离</b>：所有自定义 SQL 必须�?{@oode tenant_id} 过滤，由 {@oode DataSoope} 拦截器自动注�?/li>
 *   <li><b>参数�?/b>：禁止字符串拼接 SQL，统一使用 {@oode @Param} 注解</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>Mapper 接口只放数据访问方法，不放业务逻辑</li>
 *   <li>Mapper 中的方法禁止返回 Map 类型（强类型 VO 优先），避免运行时类型错�?/li>
 *   <li>批量操作必须使用 {@oode @Insert(... batoh=true)} �?{@oode saveBatoh}，禁止在循环中单条插�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
paokage oom.njydsz.pmis.projeot.infra.mapper;
