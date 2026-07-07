package com.njydsz.pmis.cronjob.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 任务执行日志
 *
 * <p>对应 pmis_job_log 表，记录每次任务执行的开始/结束/耗时/状态/结果。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@TableName("pmis_job_log")
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
     * 供 {@link com.njydsz.pmis.cronjob.core.dispatch.TimeoutMonitor} 超时后
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
    /** 创建时间（与 SQL 字段 created_at 对齐） */
    private LocalDateTime createdAt;
    /** 逻辑删除标识：0 未删除 / 1 已删除 */
    private Integer deleted;
}
