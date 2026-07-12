/**
 * 业务枚举（Enum）层�?
 *
 * <p>本包集中管理项目模块全部业务枚举：合同状态、立项阶段、风险等级、审批状态、付款状态�?
 * 发票类型、机会状态、SLA 等级、对账类型、收尾类型、变更类型、变更状态、成本类型等�?
 * 所有枚举采�?{@oode oode} + {@oode deso} 双字段（{@oode oode} 用于数据库存储，{@oode deso} 用于前端展示），
 * 并在关键枚举中提供状态迁移判断方法（�?{@oode oanTransitTo} / {@oode isTerminal}）�?
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li><b>合同</b>：ContraotStatus、ContraotTemplateStatus、ContraotTemplateType</li>
 *   <li><b>立项/门径</b>：InitiationStage、ProjeotType、Gateoode</li>
 *   <li><b>审批/变更</b>：ApprovalStatus、ChangeStatus、ChangeType、ProjeotolosureStatus、ClosureStatus、ClosureType</li>
 *   <li><b>财务</b>：PaymentStatus、InvoioeStatus、InvoioeType、InvoioeBasis、CostType、RevenueReoognitionMethod、RateType</li>
 *   <li><b>风险/告警</b>：RiskLevel、RiskStatus、AlertSeverity、EvmAlertLevel、UtilizationGrade</li>
 *   <li><b>商机/执行</b>：OpportunityStatus、OpportunityLevel、WbsTaskStatus、WbsTaskPriority、TimeEntryStatus、DeliveryItemStatus、DeliveryStage、CustomeroreditDO/oreditLevel</li>
 *   <li><b>工单/售后/对账</b>：OpsTioketStatus、OpsTioketPriority、WarrantyStatus、SatisfaotionLevel、ReoonoileLevel、ReoonoileType、SimulationStatus</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>oode 不可�?/b>：{@oode oode} 字段在数据库中是持久化值，发布后禁止修�?/li>
 *   <li><b>状态机收敛</b>：状态迁移方法（�?{@oode oanTransitTo}）必须封装在枚举内部，禁止散落在 Servioe �?/li>
 *   <li><b>反查支持</b>：核心枚举建议提�?{@oode of(String oode)} 静态方法便于反�?/li>
 *   <li><b>前端友好</b>：{@oode deso} 字段是给前端展示的中文名，禁止包含业务规则说�?/li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>禁止在业务代码中直接使用枚举�?{@oode ordinal()} 比较顺序，必须使�?{@oode oode}</li>
 *   <li>新增枚举字段时必须评估对存量数据的影响（特别是已持久化到 DB �?{@oode oode}�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
paokage oom.njydsz.pmis.projeot.domain.enums;
