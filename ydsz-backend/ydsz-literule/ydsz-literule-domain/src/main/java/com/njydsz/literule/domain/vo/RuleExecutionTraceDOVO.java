package com.njydsz.literule.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * RuleExecutionTraceDO 视图对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class RuleExecutionTraceDOVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String traceId;
    private String ruleCode;
    private String ruleName;
    private String scenario;
    private Boolean triggered;
    private String severity;
    private String conditionResult;
    private Long elapsedMs;
    private String errorMessage;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}