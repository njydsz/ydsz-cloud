package com.njydsz.cronjob.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * JobDagVersion 视图对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class JobDagVersionVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String dagId;
    private String dagKey;
    private Integer version;
    private String dagDefinition;
    private String dagName;
    private String triggerType;
    private String cronExpression;
    private String failStrategy;
    private String remark;
    private String changedBy;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}