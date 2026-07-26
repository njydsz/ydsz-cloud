package com.njydsz.cronjob.domain.entity.log;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 任务执行日志
 *
 * <p>对应 ydsz_job_log 表，记录每次任务执行的开始/结束/耗时/状态/结果。
 *
 * <p>P1-2: 新增执行轨迹字段（{@link #queueTime}/{@link #dispatchTime}/
 * {@link #handlerInitTime}/{@link #handlerEndTime}），支持全链路执行耗时分解分析。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@TableName("ydsz_job_log")
public class JobLogDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 任务 ID */
    private String jobId;
    /** 任务 KEY */
    private String jobKey;
    /** 开始时间 */
    private LocalDateTime startTime;
    /** 结束时间 */
    private LocalDateTime endTime;
    /** 耗时(毫秒) */
    private Long durationMs;

    /** RUNNING/SUCCESS/FAILED */
    private String status;

    /** 错误信息 */
    private String errorMessage;
    /** 参数 JSON */
    private String paramsJson;
    /** 结果 JSON */
    private String resultJson;
    /** 链路追踪 ID */
    private String traceId;
    /**
     * 触发类型（P2-2）：CRON 定时 / MANUAL 手动 / RETRY 重试 / MISFIRED Misfire 触发。
     */
    private String triggerType;
    /**
     * 持锁者标识（P0-1：hostname:pid）。
     *
     * <p>任务派发抢占分布式锁时记录锁的 value（INSTANCE_ID），
     * 供 {@link com.njydsz.cronjob.server.core.dispatch.TimeoutMonitor} 超时后
     * 通过 Lua 脚本安全释放锁（仅当 value 匹配时才 delete），避免误删其他节点持有的锁。
     */
    private String lockHolder;
    /**
     * 执行节点 ID（P0-2：hostname:port）。用于故障转移时定位任务所在节点。
     */
    private String execNodeId;
    /**
     * 执行线程 ID（P0-2）。用于超时强制中断时定位执行线程。
     */
    private Long execThreadId;
    /**
     * 分片索引（P1-4：远程派发支持）。
     *
     * <p>非分片任务为 null；分片任务为 0-based 索引。
     * 供 JobNodeReaper 故障转移时重建分片锁 key（{@code ydsz:job:lock:{jobKey}:shard:{shardIndex}}）。
     */
    private Integer shardIndex;
    /** 分片总数（P1-4：远程派发支持）。
     *
     * <p>非分片任务为 null；分片任务为 shardTotal 值。便于日志查询时识别分片任务。
     */
    private Integer shardTotal;
    /**
     * 慢任务标记（P2-1-merge：合并自 ydsz_job_slow_log）。
     *
     * <p>0=非慢 / 1=慢。由 {@code SlowTaskDetector} 在任务执行完成后
     * 根据 {@code slow_threshold_ms} 判定并标记，替代原独立 slow_log 表。
     */
    private Integer isSlow;
    /**
     * 慢任务阈值快照（毫秒，P2-1-merge）。
     *
     * <p>执行时从 {@code ydsz_job.slow_threshold_ms} 快照到日志记录，
     * NULL=未配置慢任务检测。快照保留执行时的阈值，避免后续修改 job 配置影响历史判定。
     */
    private Long slowThresholdMs;
    // ============================== P1-2: 执行轨迹字段 ==============================
    /** 入队时间（任务被 JobScanner 扫描到并入队的时刻） */
    private LocalDateTime queueTime;
    /** 派发时间（任务被 Dispatcher 从队列取出并派发的时刻） */
    private LocalDateTime dispatchTime;
    /** Handler 初始化时间（JobHandler 实例化/资源准备完成的时刻） */
    private LocalDateTime handlerInitTime;
    /** Handler 执行结束时间（JobHandler.execute() 返回的时刻，与 endTime 可能不同：endTime 含后续清理） */
    private LocalDateTime handlerEndTime;

    /** 创建时间（与 SQL 字段 created_at 对齐） */
    private LocalDateTime createdAt;
    /** 逻辑删除标识：0 未删除 / 1 已删除 */
    private Integer deleted;
}
