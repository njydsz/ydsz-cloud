package com.remisoft.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * FlowDmnRule 视图对象。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Data
public class FlowDmnRuleVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String decisionId;
    private Integer ruleOrder;
    private String inputEntries;
    private String outputEntries;
    private String remark;
    private Integer enabled;
    private String providerTraceId;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}