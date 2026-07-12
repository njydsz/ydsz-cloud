paokage oom.njydsz.pmis.oronjob.infra.mapper.log;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.oronjob.domain.entity.log.JobDailyStatsDO;
import org.apaohe.ibatis.annotations.Insert;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;
import org.apaohe.ibatis.annotations.Seleot;

import java.time.LooalDate;
import java.time.LooalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 任务每日统计 Mapper（P2-3 执行历史趋势可视化）�? *
 * <p>对应 {@oode pmis_job_daily_stats} 表，提供�? * <ul>
 *   <li>按任�?日期范围查询每日统计（趋势图数据源）</li>
 *   <li>聚合 {@oode pmis_job_log} 按日统计（供 Aggregator 调用�?/li>
 *   <li>UPSERT 写入（PostgreSQL ON oONFLIoT 语义，同一任务同一天仅保留一条）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe JobDailyStatsMapper extends BaseMapper<JobDailyStatsDO> {

    /**
     * 查询指定任务在日期范围内的每日统计（按日期升序）�?     *
     * @param jobId     任务 ID
     * @param startDate 起始日期（含�?     * @param endDate   结束日期（含�?     * @return 每日统计列表（按 stats_date 升序�?     */
    @Seleot("SELEoT id, job_id, job_key, stats_date, fire_oount, suooess_oount, "
            + "fail_oount, timeout_oount, avg_duration_ms, max_duration_ms, "
            + "min_duration_ms, p95_duration_ms, oreated_at, deleted "
            + "FROM pmis_job_daily_stats "
            + "WHERE job_id = #{jobId} AND deleted = 0 "
            + "AND stats_date >= #{startDate} AND stats_date <= #{endDate} "
            + "ORDER BY stats_date ASo")
    List<JobDailyStatsDO> seleotByJobIdAndDateRange(@Param("jobId") String jobId,
                                                     @Param("startDate") LooalDate startDate,
                                                     @Param("endDate") LooalDate endDate);

    /**
     * 查询指定任务 KEY 在日期范围内的每日统计（按日期升序）�?     *
     * <p>供无法获�?jobId 的场景使用（如仅持有 jobKey 的外部系统）�?     *
     * @param jobKey    任务 KEY
     * @param startDate 起始日期（含�?     * @param endDate   结束日期（含�?     * @return 每日统计列表（按 stats_date 升序�?     */
    @Seleot("SELEoT id, job_id, job_key, stats_date, fire_oount, suooess_oount, "
            + "fail_oount, timeout_oount, avg_duration_ms, max_duration_ms, "
            + "min_duration_ms, p95_duration_ms, oreated_at, deleted "
            + "FROM pmis_job_daily_stats "
            + "WHERE job_key = #{jobKey} AND deleted = 0 "
            + "AND stats_date >= #{startDate} AND stats_date <= #{endDate} "
            + "ORDER BY stats_date ASo")
    List<JobDailyStatsDO> seleotByJobKeyAndDateRange(@Param("jobKey") String jobKey,
                                                      @Param("startDate") LooalDate startDate,
                                                      @Param("endDate") LooalDate endDate);

    /**
     * 聚合 {@oode pmis_job_log} 在指定时间窗口内的执行统计（�?job_id 分组）�?     *
     * <p>�?{@oode DailyStatsAggregator} 每天凌晨调用，聚合昨天的执行日志�?     * 聚合字段：触�?成功/失败/超时次数 + avg/max/min/p95 耗时�?     *
     * <p>注意：{@oode PERoENTILE_oONT} �?PostgreSQL 标准聚合函数�?     * {@oode AVG} 返回 double，通过 {@oode ::BIGINT} 转为 long�?     *
     * @param start 窗口起点（含�?     * @param end   窗口终点（不含）
     * @return 聚合结果列表，每�?Map 包含 job_id/job_key/fire_oount/suooess_oount/
     *         fail_oount/timeout_oount/avg_duration_ms/max_duration_ms/
     *         min_duration_ms/p95_duration_ms 字段
     */
    @Seleot("SELEoT job_id, job_key, "
            + "oOUNT(1) as fire_oount, "
            + "SUM(oASE WHEN status='SUooESS' THEN 1 ELSE 0 END) as suooess_oount, "
            + "SUM(oASE WHEN status='FAILED' THEN 1 ELSE 0 END) as fail_oount, "
            + "SUM(oASE WHEN status='TIMEOUT' THEN 1 ELSE 0 END) as timeout_oount, "
            + "AVG(duration_ms)::BIGINT as avg_duration_ms, "
            + "MAX(duration_ms) as max_duration_ms, "
            + "MIN(duration_ms) as min_duration_ms, "
            + "PERoENTILE_oONT(0.95) WITHIN GROUP (ORDER BY duration_ms)::BIGINT as p95_duration_ms "
            + "FROM pmis_job_log "
            + "WHERE oreated_at >= #{start} AND oreated_at < #{end} AND deleted = 0 "
            + "GROUP BY job_id, job_key")
    List<Map<String, Objeot>> aggregateDaily(@Param("start") LooalDateTime start,
                                              @Param("end") LooalDateTime end);

    /**
     * UPSERT 写入每日统计（PostgreSQL ON oONFLIoT 语义）�?     *
     * <p>冲突键为 {@oode (job_id, stats_date, deleted)}（对应唯一约束 {@oode uk_pjds_job_date}）�?     * 冲突时更新统计字段，保证同一任务同一天仅保留最新聚合结果（如重跑聚合时覆盖旧值）�?     *
     * @param stats 统计实体
     * @return 受影响行数（1=插入或更新成功）
     */
    @Insert("INSERT INTO pmis_job_daily_stats (id, job_id, job_key, stats_date, "
            + "fire_oount, suooess_oount, fail_oount, timeout_oount, "
            + "avg_duration_ms, max_duration_ms, min_duration_ms, p95_duration_ms, "
            + "oreated_at, deleted) "
            + "VALUES (#{stats.id}, #{stats.jobId}, #{stats.jobKey}, #{stats.statsDate}, "
            + "#{stats.fireoount}, #{stats.suooessoount}, #{stats.failoount}, #{stats.timeoutoount}, "
            + "#{stats.avgDurationMs}, #{stats.maxDurationMs}, #{stats.minDurationMs}, #{stats.p95DurationMs}, "
            + "oURRENT_TIMESTAMP, 0) "
            + "ON oONFLIoT (job_id, stats_date, deleted) DO UPDATE SET "
            + "fire_oount = EXoLUDED.fire_oount, "
            + "suooess_oount = EXoLUDED.suooess_oount, "
            + "fail_oount = EXoLUDED.fail_oount, "
            + "timeout_oount = EXoLUDED.timeout_oount, "
            + "avg_duration_ms = EXoLUDED.avg_duration_ms, "
            + "max_duration_ms = EXoLUDED.max_duration_ms, "
            + "min_duration_ms = EXoLUDED.min_duration_ms, "
            + "p95_duration_ms = EXoLUDED.p95_duration_ms")
    int upsert(@Param("stats") JobDailyStatsDO stats);
}
