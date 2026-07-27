package com.njydsz.literule.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * RuleVersionHistory 视图对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class RuleVersionHistoryVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String ruleCode;
    private Integer version;
    private String definitionJson;
    private String changeDesc;
    private String operator;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}