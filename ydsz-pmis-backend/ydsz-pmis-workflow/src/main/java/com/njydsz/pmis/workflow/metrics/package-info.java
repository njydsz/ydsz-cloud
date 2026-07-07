/**
 * 工作流 Prometheus 指标埋点。
 *
 * <p>基于 Micrometer 暴露引擎运行期关键指标（实例 / 任务 / 抄送 / 通知等），供
 * Grafana 看板、Prometheus 告警、SLO 监控使用。所有指标统一以 {@code pmis_flow_} 为前缀。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@link com.njydsz.pmis.workflow.metrics.FlowMetrics} - 指标收集器
 *   <ul>
 *     <li>Counter：实例创建 / 完成 / 终止 / 驳回、任务创建 / 通过 / 驳回 / 转办 / 委派 / 催办 / 签收</li>
 *     <li>Timer：实例总耗时、任务处理耗时</li>
 *     <li>Gauge：运行中实例数、待办任务数、抄送未读数</li>
 *   </ul></li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>Counter / Timer 走缓存（{@code ConcurrentMap}）避免重复注册。</li>
 *   <li>Gauge 通过弱引用包装，避免内存泄漏。</li>
 *   <li>Mapper 注入使用 {@code ObjectProvider} + {@code @Autowired(required=false)}，
 *       避免监控依赖造成循环依赖。</li>
 *   <li>指标名称 / Tag 集合变更属于"破坏性变更"，需同步更新 Grafana 看板与告警规则。</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.workflow.metrics;
