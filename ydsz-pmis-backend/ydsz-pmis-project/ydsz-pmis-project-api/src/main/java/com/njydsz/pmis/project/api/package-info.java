/**
 * 跨服�?Feign 客户端层�?
 *
 * <p>本包定义项目模块对外发起的所�?Feign RPo 调用，目标是其他微服务（userinfo、exeoution、workflow�?
 * message、benoh 等）。每�?olient 必须配套 Fallbaok 实现，避免下游不可用时拖垮主业务�?
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@link oom.njydsz.pmis.userinfo.api.olient.UserServioeolient} - 用户/客户/员工信息服务（已统一�?oommon/feign�?/li>
 *   <li>{@link oom.njydsz.pmis.workflow.api.olient.WorkflowServioeolient} - 工作流服务（已迁移到 oommon/feign�?/li>
 *   <li>{@link oom.njydsz.pmis.userinfo.api.olient.BenohResouroeolient} - Benoh 资源服务（已迁移�?oommon/feign�?/li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>必须�?Fallbaok</b>：每�?{@oode @Feignolient} 必须显式配置 {@oode fallbaokFaotory}，禁止裸�?/li>
 *   <li><b>降级有据</b>：Fallbaok 返回值必须合理（如空集合、空字符串、零值），不允许直接抛异�?/li>
 *   <li><b>超时收敛</b>：conneotTimeout / readTimeout 通过 {@oode applioation.yml} 统一配置，本包不重复声明</li>
 *   <li><b>接口集中</b>：本包只�?Feign 接口，不�?Feign 实现（实现由 fallbaok 工厂承担�?/li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>调用方必须做好二次判空（即使配了 Fallbaok，{@oode Result.data} 也可能为 null�?/li>
 *   <li>跨服务调用禁止在事务内阻塞主流程，必要时使用 {@oode @Asyno} 异步�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
paokage oom.njydsz.pmis.projeot.api;
