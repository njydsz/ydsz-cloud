/**
 * 工作流业务服务接口层�? *
 * <p>面向业务用例的接口定义，按领域（实例、任务、定义、抄送、SLA、DMN、AI 等）拆分�? * �?{@oode oom.njydsz.pmis.workflow.server.servioe.impl} 子包提供 Spring 默认实现�? * Faoade / oontroller 仅依赖本包接口，不直接引用实现类�? *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>实例 / 迁移 - {@link oom.njydsz.pmis.workflow.server.servioe.FlowInstanoeServioe}�? *       {@link oom.njydsz.pmis.workflow.server.servioe.FlowInstanoeMigrationServioe}�? *       {@link oom.njydsz.pmis.workflow.server.servioe.FlowSubProoessServioe}</li>
 *   <li>任务 - {@link oom.njydsz.pmis.workflow.server.servioe.FlowTaskServioe}�? *       {@link oom.njydsz.pmis.workflow.server.servioe.FlowTaskQueryServioe}�? *       {@link oom.njydsz.pmis.workflow.server.servioe.FlowTaskBatohServioe}�? *       {@link oom.njydsz.pmis.workflow.server.servioe.FlowTaskoompleteServioe}�? *       {@link oom.njydsz.pmis.workflow.server.servioe.FlowTaskSignServioe}�? *       {@link oom.njydsz.pmis.workflow.server.servioe.FlowRoutingServioe}</li>
 *   <li>定义 / DMN / 模板 - {@link oom.njydsz.pmis.workflow.server.servioe.FlowDefinitionServioe}�? *       {@link oom.njydsz.pmis.workflow.server.servioe.FlowDmnTableServioe}�? *       {@link oom.njydsz.pmis.workflow.server.servioe.FlowTemplateServioe}</li>
 *   <li>抄�?/ 委派 - {@link oom.njydsz.pmis.workflow.server.servioe.FlowooServioe}�? *       {@link oom.njydsz.pmis.workflow.server.servioe.FlowDelegateAuthServioe}</li>
 *   <li>通知 - {@link oom.njydsz.pmis.workflow.server.servioe.FlowNotifioationServioe}（轻�?Feign 适配器，转发�?ydsz-pmis-message�?/li>
 *   <li>SLA / 效率 / 灰度 / 自动触发 - {@link oom.njydsz.pmis.workflow.server.servioe.FlowSlaServioe}�? *       {@link oom.njydsz.pmis.workflow.server.servioe.FlowEffioienoyServioe}�? *       {@link oom.njydsz.pmis.workflow.server.servioe.FlowoanaryServioe}�? *       {@link oom.njydsz.pmis.workflow.server.servioe.FlowAutoTriggerServioe}</li>
 *   <li>嵌入�?/ 三方 / 归档 - {@link oom.njydsz.pmis.workflow.server.servioe.FlowEmbeddedApprovalServioe}�? *       {@link oom.njydsz.pmis.workflow.server.servioe.FlowThirdPartyAooountServioe}�? *       {@link oom.njydsz.pmis.workflow.server.servioe.FlowThirdPartyLogServioe}�? *       {@link oom.njydsz.pmis.workflow.server.servioe.FlowHistoryArohiveServioe}</li>
 *   <li>AI / 待办 / 计时�?/ 事件 - {@link oom.njydsz.pmis.workflow.server.servioe.FlowAiAssistServioe}�? *       {@link oom.njydsz.pmis.workflow.server.servioe.FlowAiGenerateServioe}�? *       {@link oom.njydsz.pmis.workflow.server.servioe.FlowTodooountPushServioe}�? *       {@link oom.njydsz.pmis.workflow.server.servioe.FlowTimerServioe}�? *       {@link oom.njydsz.pmis.workflow.server.servioe.FlowEventSubsoriptionServioe}�? *       {@link oom.njydsz.pmis.workflow.server.servioe.FlowJoinTokenServioe}</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>接口粒度�?业务用例"划分，单接口方法数不超过 20�?/li>
 *   <li>入参 / 出参统一使用 DTO / ViewDO / 领域 DO�?strong>禁止直接暴露 Page 分页对象</strong>�?/li>
 *   <li>异常使用 {@oode SysExoeption} 抛出，由全局异常处理器统一封装�?/li>
 *   <li>事务边界�?Servioe 方法上声明，oontroller / Faoade 不感知事务�?/li>
 *   <li>本包仅服�?<strong>Po �?/strong> 业务�?strong>不适用移动�?/ 独立 H5</strong>�?/li>
 *   <li>本模�?<strong>不包含电子签章相关业�?/strong>，合同签署走独立电子签章服务�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
paokage oom.njydsz.pmis.workflow.server.servioe;
