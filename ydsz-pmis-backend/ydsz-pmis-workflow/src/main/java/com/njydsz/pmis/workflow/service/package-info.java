/**
 * 工作流业务服务接口层。
 *
 * <p>面向业务用例的接口定义，按领域（实例、任务、定义、抄送、SLA、DMN、AI 等）拆分，
 * 由 {@code com.njydsz.pmis.workflow.service.impl} 子包提供 Spring 默认实现。
 * Facade / Controller 仅依赖本包接口，不直接引用实现类。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>实例 / 迁移 - {@link com.njydsz.pmis.workflow.service.FlowInstanceService}、
 *       {@link com.njydsz.pmis.workflow.service.FlowInstanceMigrationService}、
 *       {@link com.njydsz.pmis.workflow.service.FlowSubProcessService}</li>
 *   <li>任务 - {@link com.njydsz.pmis.workflow.service.FlowTaskService}、
 *       {@link com.njydsz.pmis.workflow.service.FlowTaskCommentService}、
 *       {@link com.njydsz.pmis.workflow.service.FlowTaskQueryService}、
 *       {@link com.njydsz.pmis.workflow.service.FlowTaskBatchService}、
 *       {@link com.njydsz.pmis.workflow.service.FlowTaskCompleteService}、
 *       {@link com.njydsz.pmis.workflow.service.FlowTaskSignService}、
 *       {@link com.njydsz.pmis.workflow.service.FlowRoutingService}</li>
 *   <li>定义 / DMN / 模板 - {@link com.njydsz.pmis.workflow.service.FlowDefinitionService}、
 *       {@link com.njydsz.pmis.workflow.service.FlowDmnTableService}、
 *       {@link com.njydsz.pmis.workflow.service.FlowTemplateService}</li>
 *   <li>抄送 / 委派 - {@link com.njydsz.pmis.workflow.service.FlowCcService}、
 *       {@link com.njydsz.pmis.workflow.service.FlowDelegateAuthService}</li>
 *   <li>通知 - {@link com.njydsz.pmis.workflow.service.FlowNotificationService}、
 *       {@link com.njydsz.pmis.workflow.service.FlowNotifyChannelService}、
 *       {@link com.njydsz.pmis.workflow.service.FlowNotifyOutboxService}</li>
 *   <li>SLA / 效率 / 灰度 / 自动触发 - {@link com.njydsz.pmis.workflow.service.FlowSlaService}、
 *       {@link com.njydsz.pmis.workflow.service.FlowEfficiencyService}、
 *       {@link com.njydsz.pmis.workflow.service.FlowCanaryService}、
 *       {@link com.njydsz.pmis.workflow.service.FlowAutoTriggerService}</li>
 *   <li>嵌入式 / 三方 / 归档 - {@link com.njydsz.pmis.workflow.service.FlowEmbeddedApprovalService}、
 *       {@link com.njydsz.pmis.workflow.service.FlowThirdPartyAccountService}、
 *       {@link com.njydsz.pmis.workflow.service.FlowThirdPartyLogService}、
 *       {@link com.njydsz.pmis.workflow.service.FlowHistoryArchiveService}</li>
 *   <li>AI / 待办 / 计时器 / 事件 - {@link com.njydsz.pmis.workflow.service.FlowAiAssistService}、
 *       {@link com.njydsz.pmis.workflow.service.FlowAiGenerateService}、
 *       {@link com.njydsz.pmis.workflow.service.FlowTodoCountPushService}、
 *       {@link com.njydsz.pmis.workflow.service.FlowTimerService}、
 *       {@link com.njydsz.pmis.workflow.service.FlowEventSubscriptionService}、
 *       {@link com.njydsz.pmis.workflow.service.FlowJoinTokenService}</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>接口粒度按"业务用例"划分，单接口方法数不超过 20。</li>
 *   <li>入参 / 出参统一使用 DTO / ViewDO / 领域 DO，<strong>禁止直接暴露 Page 分页对象</strong>。</li>
 *   <li>异常使用 {@code BizException} 抛出，由全局异常处理器统一封装。</li>
 *   <li>事务边界在 Service 方法上声明，Controller / Facade 不感知事务。</li>
 *   <li>本包仅服务 <strong>PC 端</strong> 业务，<strong>不适用移动端 / 独立 H5</strong>。</li>
 *   <li>本模块 <strong>不包含电子签章相关业务</strong>，合同签署走独立电子签章服务。</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.workflow.service;
