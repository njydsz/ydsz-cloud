package com.njydsz.pmis.scheduler.entity;

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
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务 ID */
    private Long jobId;
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
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 逻辑删除标识：0 未删除 / 1 已删除 */
    private Integer deleted;
}
