/**
 * 工作流业务服务实现层。
 *
 * <p>对应 {@code com.njydsz.pmis.workflow.server.service} 接口族的具体实现，
 * 业务编排、事务管理、Mapper 调用、Engine SPI 注入均在本包完成。
 * 实现类以 {@code @Service} / {@code @Component} 标注，由 Spring 容器管理。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>实例 - {@link com.njydsz.pmis.workflow.server.service.impl.FlowInstanceServiceImpl}、
 *       {@link com.njydsz.pmis.workflow.server.service.impl.FlowInstanceMigrationServiceImpl}、
 *       {@link com.njydsz.pmis.workflow.server.service.impl.FlowSubProcessServiceImpl}、
 *       {@link com.njydsz.pmis.workflow.server.service.impl.FlowJoinTokenServiceImpl}、
 *       {@link com.njydsz.pmis.workflow.server.service.impl.FlowTaskSupport}</li>
 *   <li>任务 - {@link com.njydsz.pmis.workflow.server.service.impl.FlowTaskServiceImpl}、
 *       {@link com.njydsz.pmis.workflow.server.service.impl.FlowTaskQueryServiceImpl}、
 *       {@link com.njydsz.pmis.workflow.server.service.impl.FlowTaskBatchServiceImpl}、
 *       {@link com.njydsz.pmis.workflow.server.service.impl.FlowTaskCompleteServiceImpl}、
 *       {@link com.njydsz.pmis.workflow.server.service.impl.FlowTaskSignServiceImpl}、
 *       {@link com.njydsz.pmis.workflow.server.service.impl.FlowRoutingServiceImpl}</li>
 *   <li>定义 / DMN / 模板 - {@link com.njydsz.pmis.workflow.server.service.impl.FlowDefinitionServiceImpl}、
 *       {@link com.njydsz.pmis.workflow.server.service.impl.FlowDmnTableServiceImpl}、
 *       {@link com.njydsz.pmis.workflow.server.service.impl.FlowTemplateServiceImpl}</li>
 *   <li>抄送 / 委派 - {@link com.njydsz.pmis.workflow.server.service.impl.FlowCcServiceImpl}、
 *       {@link com.njydsz.pmis.workflow.server.service.impl.FlowDelegateAuthServiceImpl}</li>
 *   <li>通知 - {@link com.njydsz.pmis.workflow.server.service.impl.FlowNotificationServiceImpl}（轻量 Feign 适配器）、
 *       {@link com.njydsz.pmis.workflow.server.service.impl.FlowTodoCountPushServiceImpl}</li>
 *   <li>SLA / 效率 / 灰度 / 自动触发 - {@link com.njydsz.pmis.workflow.server.service.impl.FlowSlaServiceImpl}、
 *       {@link com.njydsz.pmis.workflow.server.service.impl.FlowEfficiencyServiceImpl}、
 *       {@link com.njydsz.pmis.workflow.server.service.impl.FlowCanaryServiceImpl}、
 *       {@link com.njydsz.pmis.workflow.server.service.impl.FlowAutoTriggerServiceImpl}</li>
 *   <li>嵌入式 / 三方 / 归档 - {@link com.njydsz.pmis.workflow.server.service.impl.FlowEmbeddedApprovalServiceImpl}、
 *       {@link com.njydsz.pmis.workflow.server.service.impl.FlowThirdPartyAccountServiceImpl}、
 *       {@link com.njydsz.pmis.workflow.server.service.impl.FlowThirdPartyLogServiceImpl}、
 *       {@link com.njydsz.pmis.workflow.server.service.impl.FlowHistoryArchiveServiceImpl}</li>
 *   <li>AI / 计时器 / 事件 - {@link com.njydsz.pmis.workflow.server.service.impl.FlowAiAssistServiceImpl}、
 *       {@link com.njydsz.pmis.workflow.server.service.impl.FlowAiGenerateServiceImpl}、
 *       {@link com.njydsz.pmis.workflow.server.service.impl.FlowTimerServiceImpl}、
 *       {@link com.njydsz.pmis.workflow.server.service.impl.FlowEventSubscriptionServiceImpl}</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>实现类之间通过构造函数注入依赖，<strong>禁止字段注入（{@code @Autowired} 字段）</strong>。</li>
 *   <li>复杂流程编排走"门面 Service"，将多步操作封装为可复用方法，避免事务碎片化。</li>
 *   <li>对外抛出的业务异常必须使用 {@code BizException}，禁止裸 {@code RuntimeException}。</li>
 *   <li>单方法事务粒度遵循"最小必要"原则，避免大事务；跨 Service 调用走 {@code Propagation.REQUIRES_NEW}。</li>
 *   <li>本包实现仅服务 <strong>PC 端</strong>，<strong>不适用移动端 / 独立 H5</strong>；
 *       <strong>不含电子签章</strong>（合同签署走独立电子签章服务）。</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.workflow.server.service.impl;
