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
 */
@Data
@TableName("pmis_job_log")
public class JobLogDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long jobId;
    private String jobKey;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationMs;

    /** RUNNING/SUCCESS/FAILED */
    private String status;

    private String errorMessage;
    private String paramsJson;
    private String resultJson;
    private String traceId;
    private LocalDateTime createTime;
    private Integer deleted;
}
