package com.njydsz.literule.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * RuleDecisionTree 视图对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class RuleDecisionTreeVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String ruleCode;
    private String ruleName;
    private String category;
    private String description;
    private String rootNode;
    private Integer priority;
    private Boolean enabled;
    private String scope;
    private Integer version;
    private String providerTraceId;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}