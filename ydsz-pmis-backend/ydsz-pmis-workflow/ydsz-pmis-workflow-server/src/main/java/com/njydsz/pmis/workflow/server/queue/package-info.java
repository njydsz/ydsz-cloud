/**
 * 工作流消息队列集成
 *
 * <p>通过 common-queue 模块将工作流生命周期事件发布到消息队列，
 * 实现跨服务异步事件分发。
 *
 * <p><b>核心组件：</b>
 * <ul>
 *   <li>{@link com.njydsz.pmis.workflow.server.queue.FlowQueueChannels} - 队列通道常量</li>
 *   <li>{@link com.njydsz.pmis.workflow.server.queue.FlowQueuePublisher} - 事件队列发布者</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
package com.njydsz.pmis.workflow.server.queue;
