package com.njydsz.pmis.cronjob.domain.entity.job;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * MapReduce 子任务记录（P0-4）。
 *
 * <p>对应 {@code pmis_job_task} 表，存储动态产生的子任务及其执行结果。
 * 一个 JobInstance（{@link #logId}）对应多个子任务，由 {@code MapTaskExecutor} 管理：
 * root task 调用 {@code context.map()} 产生子任务，框架执行后记录结果。
 *
 * <p>与 {@link JobLogDO} 的关系：
 * <ul>
 *   <li>{@link JobLogDO}：记录整个任务实例的执行日志（一对多子任务）</li>
 *   <li>本表：记录 root task 和每个子任务的执行明细（含状态/结果/错误信息）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@TableName("pmis_job_task")
public class JobTaskDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 任务 ID（关联 pmis_job.id） */
    private String jobId;

    /** 执行日志 ID（关联 pmis_job_log.id） */
    private String logId;

    /** 任务 KEY（冗余，便于查询） */
    private String jobKey;

    /** 子任务名称（root task 为 "root"，子任务为业务侧定义的 taskName） */
    private String taskName;

    /** 子任务参数 JSON */
    private String taskParams;

    /**
     * 子任务类型：ROOT 根任务 / SUB_TASK 子任务。
     *
     * <p>ROOT 类型记录 root task 的执行状态（仅一条）；SUB_TASK 记录 map() 产生的子任务。
     */
    private String taskType;

    /**
     * 执行状态：PENDING 待执行 / RUNNING 执行中 / SUCCESS 成功 / FAILED 失败。
     */
    private String status;

    /** 执行结果 JSON（ProcessResult.result 序列化后的字符串） */
    private String result;

    /** 错误信息（失败时填充） */
    private String errorMessage;

    /** 执行节点 ID（hostname:port） */
    private String execNodeId;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 逻辑删除标识：0 未删除 / 1 已删除 */
    private Integer deleted;
}
