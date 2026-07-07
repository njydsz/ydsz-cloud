/**
 * 规则引擎 - 复杂事件处理（CEP）层。
 *
 * <p>对"事件流"进行模式匹配，支持基于时间窗口 / 滑动窗口的事件关联分析：
 * <ul>
 *   <li>{@code EventPatternMatcher} - 事件模式匹配（"A 事件后 5 分钟内 B 事件"）</li>
 *   <li>{@code TimeWindow}          - 时间窗口（滚动 / 滑动 / 会话）</li>
 *   <li>{@code EventStream}         - 事件流抽象</li>
 * </ul>
 *
 * <h3>典型场景</h3>
 * <ul>
 *   <li>风险预警：连续 3 次预算超支 → 触发风险告警</li>
 *   <li>异常行为检测：同一 IP 5 分钟内登录失败 10 次 → 锁定账号</li>
 *   <li>资源监控：项目工时连续 2 周 &gt; 100h → 健康度告警</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.literule.cep;
