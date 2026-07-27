package com.njydsz.literule.domain.dto.post;

import java.math.BigDecimal;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * RulePackDO 新增请求 DTO。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class RulePackDOPostDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

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
}