/**
 * 数据传输对象（Data Transfer Object）层。
 *
 * <p>本包定义项目模块所有 Controller 接收的入参对象，统一来自前端 JSON 反序列化。
 * DTO 是 Controller 与 Service 之间的契约层，承担以下职责：
 * <ul>
 *   <li>请求参数校验（{@code jakarta.validation}）</li>
 *   <li>Swagger 文档生成（{@code @Schema}）</li>
 *   <li>与持久化对象（DO）解耦，避免外部字段直接污染数据库结构</li>
 * </ul>
 *
 * <h3>核心组件（按业务域）</h3>
 * <ul>
 *   <li><b>商机/立项</b>：OpportunityCreateDTO、OpportunityStatusDTO、OpportunityFollowDTO、InitiationCreateDTO、InitiationStageDTO</li>
 *   <li><b>合同</b>：ContractCreateDTO、ContractStatusDTO、ContractChangeDTO、ContractSupplementDTO、ContractTemplateCreateDTO、ContractTemplateStatusDTO</li>
 *   <li><b>执行/交付</b>：WbsTaskCreateDTO、WbsTaskStatusDTO、TimeEntryCreateDTO、TimeEntryApprovalDTO、DeliveryItemCreateDTO、DeliveryItemStatusDTO、DeliveryStandardCreateDTO、EvmMeasureCreateDTO</li>
 *   <li><b>财务</b>：BudgetItemDTO、PaymentCreateDTO、PaymentAllocationDTO、InvoiceCreateDTO、InvoiceApprovalDTO、ExpenseCreateDTO、RevenueCreateDTO、ProfitSnapshotDTO、ProfitSimulationCreateDTO、RateCardCreateDTO、RateCardImportDTO、RateInternalCreateDTO</li>
 *   <li><b>风险/变更/收尾</b>：RiskCreateDTO、RiskStatusDTO、ProjectChangeCreateDTO、ProjectChangeStatusDTO、ProjectClosureCreateDTO、ProjectClosureStatusDTO、GateReviewDTO、WarrantyCreateDTO、WarrantyTerminateDTO</li>
 *   <li><b>规则引擎</b>：RuleABTestDTO、RuleAiGenerateDTO、RuleNL2RuleDTO、RuleImportDTO、RuleApproveDTO、RuleRejectDTO、RuleStatusChangeDTO、RuleTestCaseSaveDTO、RuleBatchCategoryDTO、RuleBatchPriorityDTO、RuleBatchToggleDTO、RuleDependencyAddDTO、RuleABPolicySaveDTO、DecisionTableSaveDTO、ExpressionValidateDTO、TestCaseBatchRunDTO</li>
 *   <li><b>工单/审批/告警</b>：OpsTicketCreateDTO、OpsTicketAssignDTO、OpsTicketStatusDTO、ApprovalDTO、AlertDispatchDTO、AlertEventDTO、SimulationStatusDTO</li>
 *   <li><b>其他</b>：CustomerCreditDTO、SatisfactionCreateDTO、CockpitKpiVO、CockpitAlertSummaryVO、CockpitDrillDownDTO、KpiTrendVO、ExecutiveOverviewVO、ProjectGroupKpiDTO、CreditAssessmentDTO</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>不可变优先</b>：字段全部 private，配合 Lombok {@code @Data} 使用</li>
 *   <li><b>显式校验</b>：{@code @NotBlank} / {@code @NotNull} / {@code @Min} 等必须显式标注，禁止依赖 Service 层兜底</li>
 *   <li><b>分页入参</b>：分页参数统一使用 {@code com.baomidou.mybatisplus.extension.plugins.pagination.Page}，禁止自定义 PageDTO</li>
 *   <li><b>DO/VO/DTO 分离</b>：DTO 严禁继承 DO / VO 字段</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>DTO 中禁止出现 SQL 注解（{@code @TableField} 等），保持传输层纯净</li>
 *   <li>新增 DTO 时必须配套 Swagger {@code @Schema(description=...)}</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.project.domain.dto;
