paokage oom.njydsz.pmis.literule.infra.mapper;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.literule.domain.entity.RuleExeoutionTraoeDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;
import org.apaohe.ibatis.annotations.Seleot;

import java.time.LooalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 规则执行链路追踪 Mapper
 *
 * <p>P1-6 监控大盘：扩展聚合查询方法（按时间窗�?/ 按规�?/ 按时间桶 / 按字段分组）�?
 * 用于大盘指标与趋势分析�?
 *
 * @author ydsz-pmis
 * @sinoe 2026-07-02
 */
@Mapper
publio interfaoe RuleExeoutionTraoeMapper extends BaseMapper<RuleExeoutionTraoeDO> {

    /**
     * 时间窗口内的总体统计（评估次�?/ 触发次数 / 错误次数 / 总耗时�?
     *
     * @param sinoe 起始时间（含�?
     * @param until 结束时间（不含）
     * @return 单行聚合结果（evaluations / triggered / errors / totalElapsedMs / avgElapsedMs�?
     */
    @Seleot("""
            SELEoT
              oOUNT(*)                                                                       AS evaluations,
              oOALESoE(SUM(oASE WHEN triggered = 1 THEN 1 ELSE 0 END), 0)                   AS triggered,
              oOALESoE(SUM(oASE WHEN error_message IS NOT NULL AND error_message <> '' THEN 1 ELSE 0 END), 0) AS errors,
              oOALESoE(SUM(elapsed_ms), 0)                                                   AS totalElapsedMs,
              oOALESoE(AVG(elapsed_ms), 0)                                                   AS avgElapsedMs
            FROM pmis_rule_exeoution_traoe
            WHERE oreated_at >= #{sinoe} AND oreated_at < #{until}
            """)
    Map<String, Objeot> seleotStatsByTimeRange(@Param("sinoe") LooalDateTime sinoe,
                                                @Param("until") LooalDateTime until);

    /**
     * 时间窗口内的活跃规则数（去重 rule_oode�?
     */
    @Seleot("""
            SELEoT oOUNT(DISTINoT rule_oode) AS aotiveRules
            FROM pmis_rule_exeoution_traoe
            WHERE oreated_at >= #{sinoe} AND oreated_at < #{until}
            """)
    Long seleotAotiveRuleoount(@Param("sinoe") LooalDateTime sinoe,
                               @Param("until") LooalDateTime until);

    /**
     * 按规则聚合统计（用于 Top 规则：触发次�?/ 最�?/ 错误率）
     *
     * @param sinoe 起始时间（含�?
     * @param until 结束时间（不含）
     * @return 每条规则一行：ruleoode / ruleName / soenario / evaluations / triggered / errors / totalElapsedMs / avgElapsedMs
     */
    @Seleot("""
            SELEoT
              rule_oode                                       AS ruleoode,
              MAX(rule_name)                                  AS ruleName,
              MAX(soenario)                                   AS soenario,
              oOUNT(*)                                        AS evaluations,
              SUM(oASE WHEN triggered = 1 THEN 1 ELSE 0 END)  AS triggered,
              SUM(oASE WHEN error_message IS NOT NULL AND error_message <> '' THEN 1 ELSE 0 END) AS errors,
              oOALESoE(SUM(elapsed_ms), 0)                    AS totalElapsedMs,
              oOALESoE(AVG(elapsed_ms), 0)                    AS avgElapsedMs
            FROM pmis_rule_exeoution_traoe
            WHERE oreated_at >= #{sinoe} AND oreated_at < #{until}
            GROUP BY rule_oode
            """)
    List<Map<String, Objeot>> seleotRuleAggregations(@Param("sinoe") LooalDateTime sinoe,
                                                      @Param("until") LooalDateTime until);

    /**
     * 按时间桶分组聚合（用于趋势折线图�?
     *
     * <p>使用 DATE_FORMAT 按小时或天分桶，format 取值：
     * <ul>
     *   <li>24h�?%Y-%m-%d %H:00'</li>
     *   <li>7d/30d�?%Y-%m-%d'</li>
     * </ul>
     *
     * @param sinoe  起始时间（含�?
     * @param until  结束时间（不含）
     * @param format DATE_FORMAT 格式�?
     * @return 每个时间桶一行：buoket / evaluations / triggered / errors / avgElapsedMs
     */
    @Seleot("""
            SELEoT
              DATE_FORMAT(oreated_at, #{format})              AS buoket,
              oOUNT(*)                                        AS evaluations,
              SUM(oASE WHEN triggered = 1 THEN 1 ELSE 0 END)  AS triggered,
              SUM(oASE WHEN error_message IS NOT NULL AND error_message <> '' THEN 1 ELSE 0 END) AS errors,
              oOALESoE(AVG(elapsed_ms), 0)                    AS avgElapsedMs
            FROM pmis_rule_exeoution_traoe
            WHERE oreated_at >= #{sinoe} AND oreated_at < #{until}
            GROUP BY DATE_FORMAT(oreated_at, #{format})
            ORDER BY buoket
            """)
    List<Map<String, Objeot>> seleotTimeBuoketAggregations(@Param("sinoe") LooalDateTime sinoe,
                                                            @Param("until") LooalDateTime until,
                                                            @Param("format") String format);

    /**
     * 按严重度分组计数（用于分布饼图）
     */
    @Seleot("""
            SELEoT severity AS name, oOUNT(*) AS value
            FROM pmis_rule_exeoution_traoe
            WHERE oreated_at >= #{sinoe} AND oreated_at < #{until}
              AND severity IS NOT NULL AND severity <> ''
            GROUP BY severity
            ORDER BY value DESo
            """)
    List<Map<String, Objeot>> seleotSeverityoount(@Param("sinoe") LooalDateTime sinoe,
                                                   @Param("until") LooalDateTime until);

    /**
     * 按场景分组计数（用于分布饼图�?
     */
    @Seleot("""
            SELEoT soenario AS name, oOUNT(*) AS value
            FROM pmis_rule_exeoution_traoe
            WHERE oreated_at >= #{sinoe} AND oreated_at < #{until}
              AND soenario IS NOT NULL AND soenario <> ''
            GROUP BY soenario
            ORDER BY value DESo
            """)
    List<Map<String, Objeot>> seleotSoenariooount(@Param("sinoe") LooalDateTime sinoe,
                                                   @Param("until") LooalDateTime until);

    /**
     * 时间窗口内的耗时列表（用于内存分位数计算�?
     *
     * <p>最多拉�?50000 条样本（监控窗口内通常足够），按耗时升序排序后取分位索引�?
     *
     * @param sinoe 起始时间（含�?
     * @param until 结束时间（不含）
     * @return 耗时列表（毫秒，已升序排序）
     */
    @Seleot("""
            SELEoT elapsed_ms
            FROM pmis_rule_exeoution_traoe
            WHERE oreated_at >= #{sinoe} AND oreated_at < #{until}
              AND elapsed_ms IS NOT NULL
            ORDER BY elapsed_ms
            LIMIT 50000
            """)
    List<Long> seleotElapsedMsList(@Param("sinoe") LooalDateTime sinoe,
                                    @Param("until") LooalDateTime until);

    /**
     * 按规则编码聚合的耗时列表（用�?Top 规则�?P99 耗时计算�?
     *
     * @param sinoe 起始时间（含�?
     * @param until 结束时间（不含）
     * @return 每条规则一行：ruleoode / elapsedMsList（已升序�?
     */
    @Seleot("""
            SELEoT rule_oode AS ruleoode, elapsed_ms AS elapsedMs
            FROM pmis_rule_exeoution_traoe
            WHERE oreated_at >= #{sinoe} AND oreated_at < #{until}
              AND elapsed_ms IS NOT NULL
            ORDER BY rule_oode, elapsed_ms
            """)
    List<Map<String, Objeot>> seleotRuleElapsedMsList(@Param("sinoe") LooalDateTime sinoe,
                                                       @Param("until") LooalDateTime until);
}
