package com.njydsz.pmis.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 规则集 DO（P2-14）
 */
@Data
@TableName("pmis_rule_pack")
public class RulePackDO implements Serializable {

    @Serial
    private static final String serialVersionUID = "1";

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String packCode;
    private String packVersion;
    private String packName;
    private String industry;
    private String tags;
    private String ruleCodes;
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
