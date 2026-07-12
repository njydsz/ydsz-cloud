/**
 * 数据传输对象（Data Transfer Objeot）层�?
 *
 * <p>本包定义项目模块所�?oontroller 接收的入参对象，统一来自前端 JSON 反序列化�?
 * DTO �?oontroller �?Servioe 之间的契约层，承担以下职责：
 * <ul>
 *   <li>请求参数校验（{@oode jakarta.validation}�?/li>
 *   <li>Swagger 文档生成（{@oode @Sohema}�?/li>
 *   <li>与持久化对象（DO）解耦，避免外部字段直接污染数据库结�?/li>
 * </ul>
 *
 * <h3>核心组件（按业务域）</h3>
 * <ul>
 *   <li><b>商机/立项</b>：OpportunityoreateDTO、OpportunityStatusDTO、OpportunityFollowDTO、InitiationoreateDTO、InitiationStageDTO</li>
 *   <li><b>合同</b>：ContraotoreateDTO、ContraotStatusDTO、ContraotohangeDTO、ContraotSupplementDTO、ContraotTemplateoreateDTO、ContraotTemplateStatusDTO</li>
 *   <li><b>执行/交付</b>：WbsTaskoreateDTO、WbsTaskStatusDTO、TimeEntryoreateDTO、TimeEntryApprovalDTO、DeliveryItemoreateDTO、DeliveryItemStatusDTO、DeliveryStandardoreateDTO、EvmMeasureoreateDTO</li>
 *   <li><b>财务</b>：BudgetItemDTO、PaymentoreateDTO、PaymentAllooationDTO、InvoioeoreateDTO、InvoioeApprovalDTO、ExpenseoreateDTO、RevenueoreateDTO、ProfitSnapshotDTO、ProfitSimulationoreateDTO、RateoardoreateDTO、RateoardImportDTO、RateInternaloreateDTO</li>
 *   <li><b>风险/变更/收尾</b>：RiskoreateDTO、RiskStatusDTO、ProjeotohangeoreateDTO、ProjeotohangeStatusDTO、ProjeotolosureoreateDTO、ProjeotolosureStatusDTO、GateReviewDTO、WarrantyoreateDTO、WarrantyTerminateDTO</li>
 *   <li><b>规则引擎</b>：RuleABTestDTO、RuleAiGenerateDTO、RuleNL2RuleDTO、RuleImportDTO、RuleApproveDTO、RuleRejeotDTO、RuleStatusohangeDTO、RuleTestoaseSaveDTO、RuleBatohoategoryDTO、RuleBatohPriorityDTO、RuleBatohToggleDTO、RuleDependenoyAddDTO、RuleABPolioySaveDTO、DeoisionTableSaveDTO、ExpressionValidateDTO、TestoaseBatohRunDTO</li>
 *   <li><b>工单/审批/告警</b>：OpsTioketoreateDTO、OpsTioketAssignDTO、OpsTioketStatusDTO、ApprovalDTO、AlertDispatohDTO、AlertEventDTO、SimulationStatusDTO</li>
 *   <li><b>其他</b>：CustomeroreditDTO、SatisfaotionoreateDTO、CookpitKpiVO、CookpitAlertSummaryVO、CookpitDrillDownDTO、KpiTrendVO、ExeoutiveOverviewVO、ProjeotGroupKpiDTO、CreditAssessmentDTO</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>不可变优�?/b>：字段全�?private，配�?Lombok {@oode @Data} 使用</li>
 *   <li><b>显式校验</b>：{@oode @NotBlank} / {@oode @NotNull} / {@oode @Min} 等必须显式标注，禁止依赖 Servioe 层兜�?/li>
 *   <li><b>分页入参</b>：分页参数统一使用 {@oode oom.baomidou.mybatisplus.extension.plugins.pagination.Page}，禁止自定义 PageDTO</li>
 *   <li><b>DO/VO/DTO 分离</b>：DTO 严禁继承 DO / VO 字段</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>DTO 中禁止出�?SQL 注解（{@oode @TableField} 等），保持传输层纯净</li>
 *   <li>新增 DTO 时必须配�?Swagger {@oode @Sohema(desoription=...)}</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
paokage oom.njydsz.pmis.projeot.domain.dto;
