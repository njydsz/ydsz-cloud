/**
 * 统一告警事件总线 (P0-2 架构优化)。
 *
 * <p>提供全局统一的告警事件模型和分发器，各模块通过
 * {@link com.njydsz.pmis.common.alert.UnifiedAlertEvent} 发布告警，
 * 由 {@link com.njydsz.pmis.common.alert.UnifiedAlertDispatcher} 统一消费并分发到 message 模块。
 *
 * <h3>架构分层</h3>
 * <ul>
 *   <li><b>规则定义层</b>：各模块自定义告警规则（如 project 的 BudgetAlertRule、cronjob 的 JobAlertRule）</li>
 *   <li><b>事件发布层</b>：规则触发后构造 {@link com.njydsz.pmis.common.alert.UnifiedAlertEvent} 并通过 Spring 事件总线发布</li>
 *   <li><b>统一分发层</b>：{@link com.njydsz.pmis.common.alert.UnifiedAlertDispatcher} 消费事件，负责角色解析/通道路由/去重/重试</li>
 *   <li><b>通知发送层</b>：通过 Feign 调用 message 模块（MessageServiceClient + NotificationClient）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
package com.njydsz.pmis.common.alert;
