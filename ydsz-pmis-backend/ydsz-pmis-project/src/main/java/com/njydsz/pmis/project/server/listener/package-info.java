/**
 * Spring 事件监听器层（Event Listener）。
 *
 * <p>本包处理项目模块内部及跨模块事件（Spring {@code ApplicationEvent}）的监听与响应，
 * 实现业务模块间的解耦。当前主要用于在项目变更执行后触发 EVM 基线重算、告警派发等
 * 联动行为。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@link com.njydsz.pmis.project.server.listener.ProjectChangeExecutedEventListener} - 项目变更执行后联动 EVM 重算 + 告警</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>解耦</b>：事件发布者不感知订阅者，符合"发布-订阅"模型</li>
 *   <li><b>异步优先</b>：监听器统一标注 {@code @Async} 与 {@code @EventListener}，不阻塞发布方主事务</li>
 *   <li><b>异常隔离</b>：监听器异常不得影响发布方主流程，必须 try/catch 包裹并降级</li>
 *   <li><b>可观测</b>：监听器执行结果埋点至 Micrometer 计数器</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>监听器中禁止发起同步跨服务 RPC 长任务</li>
 *   <li>新增事件类型必须在 {@code com.njydsz.pmis.common.event} 统一定义，避免散落</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.project.server.listener;
