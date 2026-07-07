/**
 * 驾驶舱预警规则引擎子包（Alert Rule Engine）。
 *
 * <p>本子包实现驾驶舱 KPI 快照 → 预警事件的规则计算框架，由 {@code AlertRuleEngine} 负责
 * 收集并遍历所有 {@code AlertRule} 实现，输出 {@code AlertEventDTO} 列表。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@link com.njydsz.pmis.project.engine.alert.AlertRule} - 预警规则 SPI 接口</li>
 *   <li>{@link com.njydsz.pmis.project.engine.alert.AlertRuleEngine} - 规则执行器（注册 + 评估）</li>
 *   <li>{@link com.njydsz.pmis.project.engine.alert.MarginLowRule} - 毛利率低预警</li>
 *   <li>{@link com.njydsz.pmis.project.engine.alert.EvmRedRule} - EVM 红色预警</li>
 *   <li>{@link com.njydsz.pmis.project.engine.alert.UtilizationLowRule} - 人员利用率低预警</li>
 *   <li>{@link com.njydsz.pmis.project.engine.alert.BenchHighRule} - Bench 闲置成本高预警</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>开闭原则</b>：新增规则只需新增 {@code AlertRule} 实现类，无需修改 Engine</li>
 *   <li><b>故障隔离</b>：单条规则执行异常不影响其他规则，捕获后记录 WARN</li>
 *   <li><b>可插拔</b>：规则可通过 {@code @Component} 注解自动注册到 Engine</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>{@link com.njydsz.pmis.project.engine.alert.AlertRule#evaluate} 返回 {@code null} 表示未触发</li>
 *   <li>规则 severity 建议与 {@link com.njydsz.pmis.project.enums.AlertSeverity} 对齐</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.project.engine.alert;
