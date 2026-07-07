package com.njydsz.pmis.literule.spi;

import com.njydsz.pmis.literule.api.RuleExecutionTrace;

import java.util.List;

/**
 * 规则执行轨迹数据提供者（SPI，P3-4 自适应智能风控）
 *
 * <p>由消费方提供实现，从 {@code pmis_rule_execution_trace} 表读取历史轨迹数据，
 * 供 {@link com.njydsz.pmis.literule.adaptive.AdaptiveThresholdService} 分析规则阈值。
 * literule 模块本身不依赖持久层，通过此接口反转依赖。
 *
 * <p>与 {@link TraceRecorder} 的关系：
 * <ul>
 *   <li>{@link TraceRecorder} 负责写入轨迹（write side）</li>
 *   <li>本接口负责读取轨迹用于分析（read side）</li>
 *   <li>两者可由同一个持久化实现同时实现，但拆分为两个接口避免职责膨胀</li>
 * </ul>
 *
 * <p>实现建议：
 * <ul>
 *   <li>按 {@code created_at >= NOW() - N days} 过滤最近 N 天的数据</li>
 *   <li>默认限制返回条数（如 5000）避免内存溢出</li>
 *   <li>按 {@code created_at DESC} 排序</li>
 *   <li>未启用 trace 时返回空列表，不抛异常</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.8.0
 */
public interface TraceDataProvider {

    /**
     * 查询指定规则最近 N 天的执行轨迹
     *
     * @param ruleCode 规则编码
     * @param days     天数（最近 N 天，&le;0 表示不限制时间范围）
     * @return 轨迹列表（按创建时间倒序）；无数据时返回空列表
     */
    List<RuleExecutionTrace> getTracesByRule(String ruleCode, int days);

    /**
     * 查询最近 N 天的全部执行轨迹
     *
     * @param days  天数（最近 N 天，&le;0 表示不限制时间范围）
     * @param limit 最大返回条数（&le;0 表示使用实现默认值）
     * @return 轨迹列表（按创建时间倒序）；无数据时返回空列表
     */
    List<RuleExecutionTrace> getRecentTraces(int days, int limit);

    /**
     * 是否启用轨迹数据提供
     *
     * <p>返回 false 时，{@link com.njydsz.pmis.literule.adaptive.AdaptiveThresholdService}
     * 会跳过分析并返回空结果，避免无数据源时抛异常。
     *
     * @return true=已启用并提供数据
     */
    default boolean isAvailable() {
        return true;
    }
}
