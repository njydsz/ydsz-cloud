package com.njydsz.cronjob.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * JobLogContent 视图对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class JobLogContentVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String logId;
    private String jobKey;
    private Integer lineNo;
    private String logLevel;
    private String content;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}