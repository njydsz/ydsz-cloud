/**
 * 项目管理消息队列集成
 *
 * <p>通过 common-queue 模块实现项目预算告警事件分发和工作流事件消费。
 *
 * <p><b>核心组件：</b>
 * <ul>
 *   <li>{@link com.njydsz.pmis.project.server.queue.ProjectQueueChannels} - 队列通道常量</li>
 *   <li>{@link com.njydsz.pmis.project.server.queue.ProjectQueuePublisher} - 预算告警队列发布者</li>
 *   <li>{@link com.njydsz.pmis.project.server.queue.WorkflowEventQueueSubscriber} - 工作流事件订阅者</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
package com.njydsz.pmis.project.server.queue;
