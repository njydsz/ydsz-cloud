package com.njydsz.cronjob.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * JobLogContent 视图对象。
 *
 * <p>用于 Controller 层返回任务执行逐行日志数据，对应实体 {@link com.njydsz.cronjob.domain.entity.log.JobLogContent}。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class JobLogContentVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    private String id;

    /** 任务执行日志 ID（关联 ydsz_job_log.id） */
    private String logId;

    /** 任务 KEY（冗余） */
    private String jobKey;

    /** 行号（从 1 递增） */
    private Integer lineNo;

    /** 日志级别: DEBUG / INFO / WARN / ERROR */
    private String logLevel;

    /** 日志内容（单行文本，最长 4000 字符） */
    private String content;

    /** 创建人 */
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新人 */
    private String updatedBy;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
