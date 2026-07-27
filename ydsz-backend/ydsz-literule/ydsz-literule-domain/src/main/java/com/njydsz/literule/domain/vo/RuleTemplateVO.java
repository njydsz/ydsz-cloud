package com.njydsz.literule.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * RuleTemplate 视图对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class RuleTemplateVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String templateCode;
    private String templateName;
    private String category;
    private String description;
    private String conditionExpression;
    private String severityExpression;
    private String defaultSeverity;
    private String titleTemplate;
    private String descriptionTemplate;
    private Integer priority;
    private String scope;
    private String industry;
    private String tags;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}