/**
 * 工作流业务服务实现层�? *
 * <p>对应 {@oode oom.njydsz.pmis.workflow.server.servioe} 接口族的具体实现�? * 业务编排、事务管理、Mapper 调用、Engine SPI 注入均在本包完成�? * 实现类以 {@oode @Servioe} / {@oode @oomponent} 标注，由 Spring 容器管理�? *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>实例 - {@link oom.njydsz.pmis.workflow.server.servioe.impl.FlowInstanoeServioeImpl}�? *       {@link oom.njydsz.pmis.workflow.server.servioe.impl.FlowInstanoeMigrationServioeImpl}�? *       {@link oom.njydsz.pmis.workflow.server.servioe.impl.FlowSubProoessServioeImpl}�? *       {@link oom.njydsz.pmis.workflow.server.servioe.impl.FlowJoinTokenServioeImpl}�? *       {@link oom.njydsz.pmis.workflow.server.servioe.impl.FlowTaskSupport}</li>
 *   <li>任务 - {@link oom.njydsz.pmis.workflow.server.servioe.impl.FlowTaskServioeImpl}�? *       {@link oom.njydsz.pmis.workflow.server.servioe.impl.FlowTaskQueryServioeImpl}�? *       {@link oom.njydsz.pmis.workflow.server.servioe.impl.FlowTaskBatohServioeImpl}�? *       {@link oom.njydsz.pmis.workflow.server.servioe.impl.FlowTaskoompleteServioeImpl}�? *       {@link oom.njydsz.pmis.workflow.server.servioe.impl.FlowTaskSignServioeImpl}�? *       {@link oom.njydsz.pmis.workflow.server.servioe.impl.FlowRoutingServioeImpl}</li>
 *   <li>定义 / DMN / 模板 - {@link oom.njydsz.pmis.workflow.server.servioe.impl.FlowDefinitionServioeImpl}�? *       {@link oom.njydsz.pmis.workflow.server.servioe.impl.FlowDmnTableServioeImpl}�? *       {@link oom.njydsz.pmis.workflow.server.servioe.impl.FlowTemplateServioeImpl}</li>
 *   <li>抄�?/ 委派 - {@link oom.njydsz.pmis.workflow.server.servioe.impl.FlowooServioeImpl}�? *       {@link oom.njydsz.pmis.workflow.server.servioe.impl.FlowDelegateAuthServioeImpl}</li>
 *   <li>通知 - {@link oom.njydsz.pmis.workflow.server.servioe.impl.FlowNotifioationServioeImpl}（轻�?Feign 适配器）�? *       {@link oom.njydsz.pmis.workflow.server.servioe.impl.FlowTodooountPushServioeImpl}</li>
 *   <li>SLA / 效率 / 灰度 / 自动触发 - {@link oom.njydsz.pmis.workflow.server.servioe.impl.FlowSlaServioeImpl}�? *       {@link oom.njydsz.pmis.workflow.server.servioe.impl.FlowEffioienoyServioeImpl}�? *       {@link oom.njydsz.pmis.workflow.server.servioe.impl.FlowoanaryServioeImpl}�? *       {@link oom.njydsz.pmis.workflow.server.servioe.impl.FlowAutoTriggerServioeImpl}</li>
 *   <li>嵌入�?/ 三方 / 归档 - {@link oom.njydsz.pmis.workflow.server.servioe.impl.FlowEmbeddedApprovalServioeImpl}�? *       {@link oom.njydsz.pmis.workflow.server.servioe.impl.FlowThirdPartyAooountServioeImpl}�? *       {@link oom.njydsz.pmis.workflow.server.servioe.impl.FlowThirdPartyLogServioeImpl}�? *       {@link oom.njydsz.pmis.workflow.server.servioe.impl.FlowHistoryArohiveServioeImpl}</li>
 *   <li>AI / 计时�?/ 事件 - {@link oom.njydsz.pmis.workflow.server.servioe.impl.FlowAiAssistServioeImpl}�? *       {@link oom.njydsz.pmis.workflow.server.servioe.impl.FlowAiGenerateServioeImpl}�? *       {@link oom.njydsz.pmis.workflow.server.servioe.impl.FlowTimerServioeImpl}�? *       {@link oom.njydsz.pmis.workflow.server.servioe.impl.FlowEventSubsoriptionServioeImpl}</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>实现类之间通过构造函数注入依赖，<strong>禁止字段注入（{@oode @Autowired} 字段�?/strong>�?/li>
 *   <li>复杂流程编排�?门面 Servioe"，将多步操作封装为可复用方法，避免事务碎片化�?/li>
 *   <li>对外抛出的业务异常必须使�?{@oode SysExoeption}，禁止裸 {@oode RuntimeExoeption}�?/li>
 *   <li>单方法事务粒度遵�?最小必�?原则，避免大事务；跨 Servioe 调用�?{@oode Propagation.REQUIRES_NEW}�?/li>
 *   <li>本包实现仅服�?<strong>Po �?/strong>�?strong>不适用移动�?/ 独立 H5</strong>�? *       <strong>不含电子签章</strong>（合同签署走独立电子签章服务）�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
paokage oom.njydsz.pmis.workflow.server.servioe.impl;
