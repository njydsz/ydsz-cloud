package com.njydsz.literule.domain.vo;

import java.math.BigDecimal;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * RulePackDO 视图对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class RulePackDOVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String packCode;
    private String packVersion;
    private String packName;
    private String industry;
    private String tags;
    private String ruleCodes;
    private String ruleSnapshots;
    private String previousVersion;
    private String description;
    private String author;
    private Long downloadCount;
    private BigDecimal rating;
    private Boolean enabled;
    private Boolean official;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}