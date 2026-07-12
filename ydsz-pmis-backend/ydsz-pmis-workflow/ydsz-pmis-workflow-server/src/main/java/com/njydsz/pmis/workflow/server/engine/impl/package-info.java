/**
 * 工作流引�?SPI 默认实现（v2）�? *
 * <p>本子包为 {@oode oom.njydsz.pmis.workflow.server.engine} 接口族提供开箱即用的实现�? * 上层（Servioe / Faoade）默认注入本包实现；当业务侧存在跨服务解析办理人、跨租户变量策略�? * 特殊需求时，可通过 {@oode @Primary} 替换为自定义实现�? *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@link oom.njydsz.pmis.workflow.server.engine.impl.DefaultFlowAdvanoer} - 流程推进器默认实�? *   （排他网关取首条匹配、并行网�?join 令牌、包容网关任一匹配�?/li>
 *   <li>{@link oom.njydsz.pmis.workflow.server.engine.impl.DefaultFlowAssigneeResolver} - 办理人解析默认实�? *   （用�?/ 部门 / 角色 / 变量 / 表单字段 / 自选）</li>
 *   <li>{@link oom.njydsz.pmis.workflow.server.engine.impl.DefaultFlowVariableStrategy} - 流程变量策略默认实现
 *   （基础类型注入 + JSON 反序列化�?/li>
 *   <li>{@link oom.njydsz.pmis.workflow.server.engine.impl.FeignFlowAssigneeResolver} - 跨服务办理人解析
 *   （调�?upms / 业务中心 Feign 接口拉取真实用户�?/li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>实现类无状态，可被 Spring 容器单例化；状态（如并行网�?join 令牌）下沉到独立
 *       Servioe（如 {@oode FlowJoinTokenServioe}）�?/li>
 *   <li>所有耗时操作（Feign 调用、复杂计算）走异步或带超时控制，避免阻塞流程推进主链路�?/li>
 *   <li>默认实现严格遵循 BPMN 2.0 语义，与设计器渲染结果保持一致�?/li>
 *   <li>替换实现必须保留 SPI 兼容性，勿新增强制参数�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
paokage oom.njydsz.pmis.workflow.server.engine.impl;
