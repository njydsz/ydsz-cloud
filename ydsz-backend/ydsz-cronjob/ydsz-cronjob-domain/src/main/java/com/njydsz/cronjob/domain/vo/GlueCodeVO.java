package com.njydsz.cronjob.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * GlueCode 视图对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class GlueCodeVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String jobId;
    private String sourceCode;
    private String language;
    private Integer version;
    private String remark;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}