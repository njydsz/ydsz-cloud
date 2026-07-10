/**
 * 工作流数据传输对象（DTO）。
 *
 * <p>用于在 Controller / Service / Engine 各层之间传递结构化数据，避免上层依赖 DO 与内部模型。
 * 所有 DTO 均为可序列化 POJO，复杂字段使用 Jakarta Validation 注解做参数校验。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>启动 / 部署类 - {@link com.njydsz.pmis.workflow.dto.FlowStartProcessDTO}、
 *       {@link com.njydsz.pmis.workflow.dto.FlowDeployProcessDTO}、
 *       {@link com.njydsz.pmis.workflow.dto.FlowAutoTriggerCreateDTO}</li>
 *   <li>任务操作类 - {@link com.njydsz.pmis.workflow.dto.FlowTaskOperateDTO}、
 *       {@link com.njydsz.pmis.workflow.dto.FlowAssigneeDTO}</li>
 *   <li>流程变量 / 视图类 - {@link com.njydsz.pmis.workflow.dto.FlowInstanceVariablesDTO}、
 *       {@link com.njydsz.pmis.workflow.dto.FlowInstanceViewDTO}</li>
 *   <li>DMN 类 - {@link com.njydsz.pmis.workflow.dto.DmnExecuteDTO}、
 *       {@link com.njydsz.pmis.workflow.dto.FlowDmnTableSaveDTO}</li>
 *   <li>设计器类 - {@link com.njydsz.pmis.workflow.dto.FlowDesignerDataDTO}、
 *       {@link com.njydsz.pmis.workflow.dto.FlowDefinitionSimulateDTO}</li>
 *   <li>AI 类 - {@link com.njydsz.pmis.workflow.dto.FlowAiGenerateDTO}、
 *       {@link com.njydsz.pmis.workflow.dto.FlowAiRecommendApproversDTO}、
 *       {@link com.njydsz.pmis.workflow.dto.FlowAiDraftCommentDTO}</li>
 *   <li>嵌入式审批类 - {@link com.njydsz.pmis.workflow.dto.EmbeddedApprovalActionDTO}、
 *       {@link com.njydsz.pmis.workflow.dto.EmbeddedApprovalViewDTO}</li>
 *   <li>委派 / 抄送 / 通知类 - {@link com.njydsz.pmis.workflow.dto.FlowDelegateAuthSaveDTO}、
 *       {@link com.njydsz.pmis.workflow.dto.FlowCcQueryDTO}、
 *       {@link com.njydsz.pmis.workflow.dto.FlowCommentCreateDTO}</li>
 *   <li>实例迁移类 - {@link com.njydsz.pmis.workflow.dto.InstanceMigrationDTO}、
 *       {@link com.njydsz.pmis.workflow.dto.InstanceMigrationResultDTO}</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>DTO 字段命名遵循 JSON 小驼峰风格，与 {@code @RequestBody} 反序列化严格对齐。</li>
 *   <li>必填字段统一使用 {@code @NotBlank} / {@code @NotNull} 注解，错误信息采用 i18n Key。</li>
 *   <li>DTO 不包含任何业务行为，只承担数据搬运。</li>
 *   <li>敏感字段（如个人隐私）在序列化阶段统一由 Jackson 过滤器裁剪。</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.workflow.dto;
