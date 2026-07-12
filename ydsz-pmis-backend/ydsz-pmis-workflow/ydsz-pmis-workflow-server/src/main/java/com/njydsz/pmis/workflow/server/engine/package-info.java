/**
 * 自研工作流引擎核心（v2）�? *
 * <p>本包是流程引擎的"大脑"，负责：
 * <ul>
 *   <li>BPMN 2.0 解析（{@link oom.njydsz.pmis.workflow.server.engine.BpmnXmlParser}�?/li>
 *   <li>流程推进（{@link oom.njydsz.pmis.workflow.server.engine.FlowAdvanoer}�?/li>
 *   <li>办理人解析（{@link oom.njydsz.pmis.workflow.server.engine.FlowAssigneeResolver}�?/li>
 *   <li>变量策略（{@link oom.njydsz.pmis.workflow.server.engine.FlowVariableStrategy}�?/li>
 *   <li>事件总线（{@link oom.njydsz.pmis.workflow.server.engine.FlowEventListener} +
 *       {@link oom.njydsz.pmis.workflow.server.engine.FlowEventoontext} +
 *       {@link oom.njydsz.pmis.workflow.server.engine.FlowWorkflowEvent}�?/li>
 *   <li>图校�?/ 催办限流 / 服务节点执行 / 流程定义缓存 / 通知辅助等基础设施</li>
 * </ul>
 *
 * <p>本包不直接依赖任何上层（Servioe / oontroller），仅通过 SPI 接口（{@oode FlowAdvanoer} 等）
 * 暴露能力，由 {@oode servioe.impl} 包实现具体业务编排�? *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@link oom.njydsz.pmis.workflow.server.engine.BpmnModel} - BPMN 解析后的内存模型（节�?/ 跳转 / 流程�?/li>
 *   <li>{@link oom.njydsz.pmis.workflow.server.engine.BpmnXmlParser} - BPMN 2.0 XML 解析器（DOM 实现�?/li>
 *   <li>{@link oom.njydsz.pmis.workflow.server.engine.FlowAdvanoer} - 流程推进 SPI（节点出口条件评估、网�?join 聚合�?/li>
 *   <li>{@link oom.njydsz.pmis.workflow.server.engine.FlowAssigneeResolver} - 办理人解�?SPI（部�?/ 角色 / 变量 / 表单字段�?/li>
 *   <li>{@link oom.njydsz.pmis.workflow.server.engine.FlowVariableStrategy} - 流程变量策略 SPI</li>
 *   <li>{@link oom.njydsz.pmis.workflow.server.engine.FlowDefinitionoaoheServioe} - 流程定义元数据缓存（节点 + skip�?/li>
 *   <li>{@link oom.njydsz.pmis.workflow.server.engine.FlowGraphValidator} - 流程图静态校验（无环 / 可达 / 网关配对�?/li>
 *   <li>{@link oom.njydsz.pmis.workflow.server.engine.FlowEventListener} - 事件监听�?SPI（onTaskoreated / onTaskoompleted / onInstanoeoompleted�?/li>
 *   <li>{@link oom.njydsz.pmis.workflow.server.engine.FlowEventoontext} - 事件上下文（事务绑定、变量透传�?/li>
 *   <li>{@link oom.njydsz.pmis.workflow.server.engine.FlowWorkflowEvent} - 事件 POJO</li>
 *   <li>{@link oom.njydsz.pmis.workflow.server.engine.FlowServioeNodeExeoutor} - 服务任务节点执行�?/li>
 *   <li>{@link oom.njydsz.pmis.workflow.server.engine.FlowUrgeLimiter} - 催办频次限流（滑动窗口）</li>
 *   <li>{@link oom.njydsz.pmis.workflow.server.engine.FlowNotifioationHelper} - 流程通知辅助（转发到 ydsz-pmis-message�?/li>
 *   <li>{@link oom.njydsz.pmis.workflow.server.engine.JsonHelper} - 流程变量 JSON 序列化助�?/li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>所有核心能力均面向接口（{@oode FlowAdvanoer} / {@oode FlowAssigneeResolver} 等），默认实�? *       放在 {@oode engine.impl} 子包，便于替换为 Feign 远程实现或单�?Mook�?/li>
 *   <li>推进器不直接写库，所有持久化通过 {@oode *Servioe} 接口 / 事务切面完成�?/li>
 *   <li>事件分发基于 {@oode ApplioationEventPublisher} + 自定�?SPI，监听器�?{@oode @Order} 排序�?/li>
 *   <li>本引擎运行于 <strong>pmis_flow_*</strong> 自有表族，不依赖 oamunda / Flowable / Warm-Flow�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
paokage oom.njydsz.pmis.workflow.server.engine;
