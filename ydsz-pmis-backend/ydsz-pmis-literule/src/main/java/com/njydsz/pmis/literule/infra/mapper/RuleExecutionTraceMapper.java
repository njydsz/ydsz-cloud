package com.njydsz.pmis.literule.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.literule.domain.entity.RuleExecutionTraceDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 规则执行链路追踪 Mapper
 *
 * <p>P1-6 监控大盘：扩展聚合查询方法（按时间窗口 / 按规则 / 按时间桶 / 按字段分组），
 * 用于大盘指标与趋势分析。
 *
 * @author ydsz-pmis
 * @since 2026-07-02
 */
@Mapper
public interface RuleExecutionTraceMapper extends BaseMapper<RuleExecutionTraceDO> {

    /**
     * 时间窗口内的总体统计（评估次数 / 触发次数 / 错误次数 / 总耗时）
     *
     * @param since 起始时间（含）
     * @param until 结束时间（不含）
     * @return 单行聚合结果（evaluations / triggered / errors / totalElapsedMs / avgElapsedMs）
     */
    @Select("""
            SELECT
              COUNT(*)                                                                       AS evaluations,
              COALESCE(SUM(CASE WHEN triggered = 1 THEN 1 ELSE 0 END), 0)                   AS triggered,
              COALESCE(SUM(CASE WHEN error_message IS NOT NULL AND error_message <> '' THEN 1 ELSE 0 END), 0) AS errors,
              COALESCE(SUM(elapsed_ms), 0)                                                   AS totalElapsedMs,
              COALESCE(AVG(elapsed_ms), 0)                                                   AS avgElapsedMs
            FROM pmis_rule_execution_trace
            WHERE created_at >= #{since} AND created_at < #{until}
            """)
    Map<String, Object> selectStatsByTimeRange(@Param("since") LocalDateTime since,
                                                @Param("until") LocalDateTime until);

    /**
     * 时间窗口内的活跃规则数（去重 rule_code）
     */
    @Select("""
            SELECT COUNT(DISTINCT rule_code) AS activeRules
            FROM pmis_rule_execution_trace
            WHERE created_at >= #{since} AND created_at < #{until}
            """)
    Long selectActiveRuleCount(@Param("since") LocalDateTime since,
                               @Param("until") LocalDateTime until);

    /**
     * 按规则聚合统计（用于 Top 规则：触发次数 / 最慢 / 错误率）
     *
     * @param since 起始时间（含）
     * @param until 结束时间（不含）
     * @return 每条规则一行：ruleCode / ruleName / scenario / evaluations / triggered / errors / totalElapsedMs / avgElapsedMs
     */
    @Select("""
            SELECT
              rule_code                                       AS ruleCode,
              MAX(rule_name)                                  AS ruleName,
              MAX(scenario)                                   AS scenario,
              COUNT(*)                                        AS evaluations,
              SUM(CASE WHEN triggered = 1 THEN 1 ELSE 0 END)  AS triggered,
              SUM(CASE WHEN error_message IS NOT NULL AND error_message <> '' THEN 1 ELSE 0 END) AS errors,
              COALESCE(SUM(elapsed_ms), 0)                    AS totalElapsedMs,
              COALESCE(AVG(elapsed_ms), 0)                    AS avgElapsedMs
            FROM pmis_rule_execution_trace
            WHERE created_at >= #{since} AND created_at < #{until}
            GROUP BY rule_code
            """)
    List<Map<String, Object>> selectRuleAggregations(@Param("since") LocalDateTime since,
                                                      @Param("until") LocalDateTime until);

    /**
     * 按时间桶分组聚合（用于趋势折线图）
     *
     * <p>使用 DATE_FORMAT 按小时或天分桶，format 取值：
     * <ul>
     *   <li>24h：'%Y-%m-%d %H:00'</li>
     *   <li>7d/30d：'%Y-%m-%d'</li>
     * </ul>
     *
     * @param since  起始时间（含）
     * @param until  结束时间（不含）
     * @param format DATE_FORMAT 格式串
     * @return 每个时间桶一行：bucket / evaluations / triggered / errors / avgElapsedMs
     */
    @Select("""
            SELECT
              DATE_FORMAT(created_at, #{format})              AS bucket,
              COUNT(*)                                        AS evaluations,
              SUM(CASE WHEN triggered = 1 THEN 1 ELSE 0 END)  AS triggered,
              SUM(CASE WHEN error_message IS NOT NULL AND error_message <> '' THEN 1 ELSE 0 END) AS errors,
              COALESCE(AVG(elapsed_ms), 0)                    AS avgElapsedMs
            FROM pmis_rule_execution_trace
            WHERE created_at >= #{since} AND created_at < #{until}
            GROUP BY DATE_FORMAT(created_at, #{format})
            ORDER BY bucket
            """)
    List<Map<String, Object>> selectTimeBucketAggregations(@Param("since") LocalDateTime since,
                                                            @Param("until") LocalDateTime until,
                                                            @Param("format") String format);

    /**
     * 按严重度分组计数（用于分布饼图）
     */
    @Select("""
            SELECT severity AS name, COUNT(*) AS value
            FROM pmis_rule_execution_trace
            WHERE created_at >= #{since} AND created_at < #{until}
              AND severity IS NOT NULL AND severity <> ''
            GROUP BY severity
            ORDER BY value DESC
            """)
    List<Map<String, Object>> selectSeverityCount(@Param("since") LocalDateTime since,
                                                   @Param("until") LocalDateTime until);

    /**
     * 按场景分组计数（用于分布饼图）
     */
    @Select("""
            SELECT scenario AS name, COUNT(*) AS value
            FROM pmis_rule_execution_trace
            WHERE created_at >= #{since} AND created_at < #{until}
              AND scenario IS NOT NULL AND scenario <> ''
            GROUP BY scenario
            ORDER BY value DESC
            """)
    List<Map<String, Object>> selectScenarioCount(@Param("since") LocalDateTime since,
                                                   @Param("until") LocalDateTime until);

    /**
     * 时间窗口内的耗时列表（用于内存分位数计算）
     *
     * <p>最多拉取 50000 条样本（监控窗口内通常足够），按耗时升序排序后取分位索引。
     *
     * @param since 起始时间（含）
     * @param until 结束时间（不含）
     * @return 耗时列表（毫秒，已升序排序）
     */
    @Select("""
            SELECT elapsed_ms
            FROM pmis_rule_execution_trace
            WHERE created_at >= #{since} AND created_at < #{until}
              AND elapsed_ms IS NOT NULL
            ORDER BY elapsed_ms
            LIMIT 50000
            """)
    List<Long> selectElapsedMsList(@Param("since") LocalDateTime since,
                                    @Param("until") LocalDateTime until);

    /**
     * 按规则编码聚合的耗时列表（用于 Top 规则的 P99 耗时计算）
     *
     * @param since 起始时间（含）
     * @param until 结束时间（不含）
     * @return 每条规则一行：ruleCode / elapsedMsList（已升序）
     */
    @Select("""
            SELECT rule_code AS ruleCode, elapsed_ms AS elapsedMs
            FROM pmis_rule_execution_trace
            WHERE created_at >= #{since} AND created_at < #{until}
              AND elapsed_ms IS NOT NULL
            ORDER BY rule_code, elapsed_ms
            """)
    List<Map<String, Object>> selectRuleElapsedMsList(@Param("since") LocalDateTime since,
                                                       @Param("until") LocalDateTime until);
}
