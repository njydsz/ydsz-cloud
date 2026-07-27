package com.njydsz.literule.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * RuleVariableDef 视图对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class RuleVariableDefVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String varName;
    private String varType;
    private String description;
    private String sampleValue;
    private String category;
    private Boolean required;
    private Boolean enabled;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}