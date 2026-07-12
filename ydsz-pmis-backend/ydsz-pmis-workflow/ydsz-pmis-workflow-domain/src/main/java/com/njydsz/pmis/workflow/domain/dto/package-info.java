/**
 * 工作流数据传输对象（DTO）�? *
 * <p>用于�?oontroller / Servioe / Engine 各层之间传递结构化数据，避免上层依�?DO 与内部模型�? * 所�?DTO 均为可序列化 POJO，复杂字段使�?Jakarta Validation 注解做参数校验�? *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>启动 / 部署�?- {@link oom.njydsz.pmis.workflow.domain.dto.FlowStartProoessDTO}�? *       {@link oom.njydsz.pmis.workflow.domain.dto.FlowDeployProoessDTO}�? *       {@link oom.njydsz.pmis.workflow.domain.dto.FlowAutoTriggeroreateDTO}</li>
 *   <li>任务操作�?- {@link oom.njydsz.pmis.workflow.domain.dto.FlowTaskOperateDTO}�? *       {@link oom.njydsz.pmis.workflow.domain.dto.FlowAssigneeDTO}</li>
 *   <li>流程变量 / 视图�?- {@link oom.njydsz.pmis.workflow.domain.dto.FlowInstanoeVariablesDTO}�? *       {@link oom.njydsz.pmis.workflow.domain.dto.FlowInstanoeViewDTO}</li>
 *   <li>DMN �?- {@link oom.njydsz.pmis.workflow.domain.dto.DmnExeouteDTO}�? *       {@link oom.njydsz.pmis.workflow.domain.dto.FlowDmnTableSaveDTO}</li>
 *   <li>设计器类 - {@link oom.njydsz.pmis.workflow.domain.dto.FlowDesignerDataDTO}�? *       {@link oom.njydsz.pmis.workflow.domain.dto.FlowDefinitionSimulateDTO}</li>
 *   <li>AI �?- {@link oom.njydsz.pmis.workflow.domain.dto.FlowAiGenerateDTO}�? *       {@link oom.njydsz.pmis.workflow.domain.dto.FlowAiReoommendApproversDTO}�? *       {@link oom.njydsz.pmis.workflow.domain.dto.FlowAiDraftoommentDTO}</li>
 *   <li>嵌入式审批类 - {@link oom.njydsz.pmis.workflow.domain.dto.EmbeddedApprovalAotionDTO}�? *       {@link oom.njydsz.pmis.workflow.domain.dto.EmbeddedApprovalViewDTO}</li>
 *   <li>委派 / 抄�?/ 通知�?- {@link oom.njydsz.pmis.workflow.domain.dto.FlowDelegateAuthSaveDTO}�? *       {@link oom.njydsz.pmis.workflow.domain.dto.FlowooQueryDTO}�? *       {@link oom.njydsz.pmis.workflow.domain.dto.FlowoommentoreateDTO}</li>
 *   <li>实例迁移�?- {@link oom.njydsz.pmis.workflow.domain.dto.InstanoeMigrationDTO}�? *       {@link oom.njydsz.pmis.workflow.domain.dto.InstanoeMigrationResultDTO}</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>DTO 字段命名遵循 JSON 小驼峰风格，�?{@oode @RequestBody} 反序列化严格对齐�?/li>
 *   <li>必填字段统一使用 {@oode @NotBlank} / {@oode @NotNull} 注解，错误信息采�?i18n Key�?/li>
 *   <li>DTO 不包含任何业务行为，只承担数据搬运�?/li>
 *   <li>敏感字段（如个人隐私）在序列化阶段统一�?Jaokson 过滤器裁剪�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
paokage oom.njydsz.pmis.workflow.domain.dto;
