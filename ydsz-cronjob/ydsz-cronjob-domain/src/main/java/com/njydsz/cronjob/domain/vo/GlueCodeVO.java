package com.njydsz.cronjob.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * GlueCode 视图对象。
 *
 * <p>用于 Controller 层返回 GLUE 在线编码数据，对应实体 {@link com.njydsz.cronjob.domain.entity.schedule.GlueCode}。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class GlueCodeVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    private String id;

    /** 任务 ID（关联 ydsz_job.id） */
    private String jobId;

    /** 源代码（Groovy/Python/Shell/JavaScript 脚本内容） */
    private String sourceCode;

    /** 语言: GROOVY(默认) / PYTHON / SHELL / JAVASCRIPT / JAVA */
    private String language;

    /** 版本号（从 1 递增） */
    private Integer version;

    /** 版本备注 */
    private String remark;

    /** 创建人 */
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新人 */
    private String updatedBy;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
