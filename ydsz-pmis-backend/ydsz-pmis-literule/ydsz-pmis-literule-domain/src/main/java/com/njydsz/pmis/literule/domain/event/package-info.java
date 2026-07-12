/**
 * 规则引擎 - 领域事件层。
 *
 * <p>规则引擎运行时产生的事件，供业务方订阅：
 * <ul>
 *   <li>{@code RuleHitEvent}       - 规则命中事件</li>
 *   <li>{@code RuleFailedEvent}     - 规则执行失败事件</li>
 *   <li>{@code RulePublishedEvent}  - 规则发布事件</li>
 *   <li>{@code RuleRolledBackEvent} - 规则回滚事件</li>
 * </ul>
 *
 * <h3>使用场景</h3>
 * <ul>
 *   <li>规则命中后自动触发工作流审批</li>
 *   <li>规则失败时发送告警通知</li>
 *   <li>规则发布后通过 MQ 通知所有节点</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.literule.domain.event;
