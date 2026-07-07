/**
 * userinfo 模块计算引擎包。
 *
 * <p>无状态的纯计算工具集合，承载资源调度/人力资源分析领域的核心算法，被 Bench、
 * 资源分配、人员利用率等业务调用。所有方法均为 {@code public static}，无 Spring 依赖，
 * 便于单元测试与并发复用。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>BenchCostCalculator - Bench 闲置成本计算器：计算闲置天数（入池到出池或当前）、
 *       累计闲置成本（按日薪 × 闲置天数）、培训期窗口判断（默认 30 天）。</li>
 *   <li>UtilizationCalculator - 资源利用率计算器：计费利用率（Billable Utilization = 已计费人时 / 投入人时）、
 *       过载判断（同时参与活跃项目数 ≥ 3 即过载）、健康度评级（LOW/NORMAL/HIGH）。</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>纯函数优先：所有方法无副作用，输入相同则输出相同，便于单元测试与缓存。</li>
 *   <li>数值精度可控：金额计算统一保留 2 位小数（{@code RoundingMode.HALF_UP}），
 *       比率计算统一保留 4 位小数，避免浮点误差累积。</li>
 *   <li>容错友好：参数为 null 时按零值处理，重要边界条件（除零、负数）显式收敛到 0 或 false。</li>
 *   <li>阈值可枚举：核心阈值（如过载项目数 3、健康利用率 60%）以 {@code public static final} 暴露。</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>新增计算器请保持工具类形态（私有构造器 + {@code public static} 方法），不引入 Spring Bean。</li>
 *   <li>复杂计算需附带单元测试，覆盖正常值、边界值与异常输入三类场景。</li>
 *   <li>金额相关方法严禁使用 {@code double}，统一使用 {@link java.math.BigDecimal}。</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.userinfo.engine;
