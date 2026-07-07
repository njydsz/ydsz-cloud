/**
 * 业务枚举（Enum）层。
 *
 * <p>本包集中管理项目模块全部业务枚举：合同状态、立项阶段、风险等级、审批状态、付款状态、
 * 发票类型、机会状态、SLA 等级、对账类型、收尾类型、变更类型、变更状态、成本类型等。
 * 所有枚举采用 {@code code} + {@code desc} 双字段（{@code code} 用于数据库存储，{@code desc} 用于前端展示），
 * 并在关键枚举中提供状态迁移判断方法（如 {@code canTransitTo} / {@code isTerminal}）。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li><b>合同</b>：ContractStatus、ContractTemplateStatus、ContractTemplateType</li>
 *   <li><b>立项/门径</b>：InitiationStage、ProjectType、GateCode</li>
 *   <li><b>审批/变更</b>：ApprovalStatus、ChangeStatus、ChangeType、ProjectClosureStatus、ClosureStatus、ClosureType</li>
 *   <li><b>财务</b>：PaymentStatus、InvoiceStatus、InvoiceType、InvoiceBasis、CostType、RevenueRecognitionMethod、RateType</li>
 *   <li><b>风险/告警</b>：RiskLevel、RiskStatus、AlertSeverity、EvmAlertLevel、UtilizationGrade</li>
 *   <li><b>商机/执行</b>：OpportunityStatus、OpportunityLevel、WbsTaskStatus、WbsTaskPriority、TimeEntryStatus、DeliveryItemStatus、DeliveryStage、CustomerCreditDO/CreditLevel</li>
 *   <li><b>工单/售后/对账</b>：OpsTicketStatus、OpsTicketPriority、WarrantyStatus、SatisfactionLevel、ReconcileLevel、ReconcileType、SimulationStatus</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>code 不可变</b>：{@code code} 字段在数据库中是持久化值，发布后禁止修改</li>
 *   <li><b>状态机收敛</b>：状态迁移方法（如 {@code canTransitTo}）必须封装在枚举内部，禁止散落在 Service 中</li>
 *   <li><b>反查支持</b>：核心枚举建议提供 {@code of(String code)} 静态方法便于反查</li>
 *   <li><b>前端友好</b>：{@code desc} 字段是给前端展示的中文名，禁止包含业务规则说明</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>禁止在业务代码中直接使用枚举的 {@code ordinal()} 比较顺序，必须使用 {@code code}</li>
 *   <li>新增枚举字段时必须评估对存量数据的影响（特别是已持久化到 DB 的 {@code code}）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.project.enums;
