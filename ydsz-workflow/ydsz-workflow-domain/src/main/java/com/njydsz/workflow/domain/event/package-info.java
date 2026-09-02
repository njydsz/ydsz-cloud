/**
 * 流程领域事件包。
 *
 * <p>包含流程引擎的领域事件定义，用于模块间解耦通信。
 *
 * <p><b>核心事件：</b>
 *
 * <ul>
 *   <li>{@link com.njydsz.workflow.domain.event.FlowInstanceStartedEvent} — 流程实例启动事件
 *   <li>{@link com.njydsz.workflow.domain.event.FlowInstanceCompletedEvent} — 流程实例完成事件
 *   <li>{@link com.njydsz.workflow.domain.event.FlowTaskCreatedEvent} — 任务创建事件
 *   <li>{@link com.njydsz.workflow.domain.event.FlowTaskCompletedEvent} — 任务完成事件
 * </ul>
 *
 * <p><b>设计原则：</b>领域事件不可变，使用 record 或 final 字段。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
package com.njydsz.workflow.domain.event;
