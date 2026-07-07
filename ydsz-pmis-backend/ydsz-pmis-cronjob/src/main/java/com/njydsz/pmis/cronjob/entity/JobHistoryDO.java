package com.njydsz.pmis.cronjob.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 任务配置历史版本实体（P1-6 任务版本管理）。
 *
 * <p>对应 {@code pmis_job_history} 表，每次任务配置更新前自动保存一份完整 JSON 快照，
 * 支持版本列表查询、版本对比和一键回滚。回滚操作会基于历史快照恢复配置字段，
 * 同时保留当前任务的统计字段（触发次数等），并产生新的历史版本。
 *
 * <p>注意：本表不包含标准审计字段（created_by/created_at/updated_by/updated_at），
 * 仅记录 {@code changed_by}（修改人）和 {@code changed_at}（修改时间），因此不继承 {@code BaseDO}。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@TableName("pmis_job_history")
public class JobHistoryDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 任务 ID（关联 pmis_job.id） */
    private String jobId;

    /** 版本号（对应更新前的 job.version） */
    private Integer version;

    /** 完整 JobDO JSON 快照 */
    private String snapshot;

    /** 任务名称（冗余，便于列表展示） */
    private String jobName;

    /** 任务 KEY（冗余） */
    private String jobKey;

    /** 处理器（冗余） */
    private String handler;

    /** Cron 表达式（冗余） */
    private String cronExpression;

    /** 参数 JSON（冗余） */
    private String paramsJson;

    /** 备注（冗余） */
    private String remark;

    /** 修改人 ID */
    private String changedBy;

    /** 修改时间 */
    private LocalDateTime changedAt;

    /** 逻辑删除标记: 0 未删除 / 1 已删除 */
    @TableLogic
    private Integer deleted;
}
