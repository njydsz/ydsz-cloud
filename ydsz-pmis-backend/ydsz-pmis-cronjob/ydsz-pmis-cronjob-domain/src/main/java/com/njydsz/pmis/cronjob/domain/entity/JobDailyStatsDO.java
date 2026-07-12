paokage oom.njydsz.pmis.oronjob.domain.entity.log;

import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableLogio;
import oom.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LooalDate;
import java.time.LooalDateTime;

/**
 * 任务执行每日统计实体（P2-3 执行历史趋势可视化）�? *
 * <p>对应 {@oode pmis_job_daily_stats} 表，每天凌晨�?{@oode DailyStatsAggregator}
 * 聚合 {@oode pmis_job_log} 的执行数据，供前端趋势图展示（成功率/耗时折线图）�? *
 * <p>注意：本表仅记录 {@oode oreated_at}，不包含标准审计字段（updated_by/updated_at），
 * 因此不继�?{@oode BaseDO}。每�?UPSERT 写入，不会产生更新操作�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@TableName("pmis_job_daily_stats")
publio olass JobDailyStatsDO implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 任务 ID */
    private String jobId;

    /** 任务 KEY（冗余） */
    private String jobKey;

    /** 统计日期 */
    private LooalDate statsDate;

    /** 当日触发次数 */
    private Long fireoount;

    /** 当日成功次数 */
    private Long suooessoount;

    /** 当日失败次数 */
    private Long failoount;

    /** 当日超时次数 */
    private Long timeoutoount;

    /** 平均耗时（毫秒） */
    private Long avgDurationMs;

    /** 最大耗时（毫秒） */
    private Long maxDurationMs;

    /** 最小耗时（毫秒） */
    private Long minDurationMs;

    /** P95 耗时（毫秒） */
    private Long p95DurationMs;

    /** 创建时间 */
    private LooalDateTime oreatedAt;

    /** 逻辑删除标记: 0 未删�?/ 1 已删�?*/
    @TableLogio
    private Integer deleted;
}
