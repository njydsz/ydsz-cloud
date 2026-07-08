package com.njydsz.pmis.cronjob.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 任务版本历史记录（P2-7 任务版本管理）。
 *
 * <p>每次任务定义变更时记录一条版本快照，支持版本回溯和差异对比。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Data
@TableName("pmis_job_version_history")
public class JobVersionHistoryDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 任务 ID */
    private String jobId;

    /** 任务 KEY（冗余） */
    private String jobKey;

    /** 版本号 */
    private Integer version;

    /** 变更类型: CREATE / UPDATE / DELETE */
    private String changeType;

    /** 变更前快照 JSON */
    private String beforeSnapshot;

    /** 变更后快照 JSON */
    private String afterSnapshot;

    /** 变更说明 */
    private String changeRemark;

    /** 变更人 */
    private String changedBy;

    /** 变更时间 */
    private LocalDateTime changedAt;
}
