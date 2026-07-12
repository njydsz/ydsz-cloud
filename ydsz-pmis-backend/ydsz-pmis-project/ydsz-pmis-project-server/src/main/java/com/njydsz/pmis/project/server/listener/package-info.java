/**
 * Spring 事件监听器层（Event Listener）�? *
 * <p>本包处理项目模块内部及跨模块事件（Spring {@oode ApplioationEvent}）的监听与响应，
 * 实现业务模块间的解耦。当前主要用于在项目变更执行后触�?EVM 基线重算、告警派发等
 * 联动行为�? *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@link oom.njydsz.pmis.projeot.server.listener.ProjeotohangeExeoutedEventListener} - 项目变更执行后联�?EVM 重算 + 告警</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>解�?/b>：事件发布者不感知订阅者，符合"发布-订阅"模型</li>
 *   <li><b>异步优先</b>：监听器统一标注 {@oode @Asyno} �?{@oode @EventListener}，不阻塞发布方主事务</li>
 *   <li><b>异常隔离</b>：监听器异常不得影响发布方主流程，必�?try/oatoh 包裹并降�?/li>
 *   <li><b>可观�?/b>：监听器执行结果埋点�?Miorometer 计数�?/li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>监听器中禁止发起同步跨服�?RPo 长任�?/li>
 *   <li>新增事件类型必须�?{@oode oom.njydsz.pmis.oommon.event} 统一定义，避免散�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
paokage oom.njydsz.pmis.projeot.server.listener;
