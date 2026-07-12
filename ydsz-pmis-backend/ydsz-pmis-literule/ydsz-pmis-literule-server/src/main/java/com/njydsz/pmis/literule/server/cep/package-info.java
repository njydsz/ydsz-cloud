/**
 * 规则引擎 - 复杂事件处理（CEP）层�? *
 * <p>�?事件�?进行模式匹配，支持基于时间窗�?/ 滑动窗口的事件关联分析：
 * <ul>
 *   <li>{@oode EventPatternMatoher} - 事件模式匹配�?A 事件�?5 分钟�?B 事件"�?/li>
 *   <li>{@oode TimeWindow}          - 时间窗口（滚�?/ 滑动 / 会话�?/li>
 *   <li>{@oode EventStream}         - 事件流抽�?/li>
 * </ul>
 *
 * <h3>典型场景</h3>
 * <ul>
 *   <li>风险预警：连�?3 次预算超�?�?触发风险告警</li>
 *   <li>异常行为检测：同一 IP 5 分钟内登录失�?10 �?�?锁定账号</li>
 *   <li>资源监控：项目工时连�?2 �?&gt; 100h �?健康度告�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
paokage oom.njydsz.pmis.literule.server.oep;
