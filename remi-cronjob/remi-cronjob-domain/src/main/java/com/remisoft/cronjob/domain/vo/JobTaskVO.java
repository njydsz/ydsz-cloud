package com.remisoft.cronjob.domain.vo;

import java.time.LocalDateTime;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * JobTask 视图对象。
 *
 * <p>用于 Controller 层返回 MapReduce 子任务数据，对应实体 {@link com.remisoft.cronjob.domain.entity.job.JobTask}。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Data
public class JobTaskVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    private String id;

    /** 任务 ID（关联 remi_job.id） */
    private String jobId;

    /** 执行日志 ID（关联 remi_job_log.id） */
    private String logId;

    /** 任务 KEY（冗余） */
    private String jobKey;

    /** 子任务名称（root task 为 "root"，子任务为业务侧定义的 taskName） */
    private String taskName;

    /** 子任务参数 JSON */
    private String taskParams;

    /** 子任务类型: ROOT 根任务 / SUB_TASK 子任务 */
    private String taskType;

    /** 执行状态: PENDING 待执行 / RUNNING 执行中 / SUCCESS 成功 / FAILED 失败 */
    private String taskStatus;

    /** 执行结果 JSON（ProcessResult.result 序列化后的字符串） */
    private String result;

    /** 错误信息（失败时填充） */
    private String errorMessage;

    /** 执行节点 ID（hostname:port） */
    private String execNodeId;

    /** 重试次数（每次重试递增） */
    private Integer retryCount;

    /** 创建人 */
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新人 */
    private String updatedBy;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
