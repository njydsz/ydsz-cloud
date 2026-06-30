package com.njydsz.pmis.execution.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 服务满意度评价实体
 *
 * <p>工单关闭 / 质保期结束时可触发；4 维度（专业度/及时性/质量/态度）各 1-5 星 + 总体评分 + 评论。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@TableName("pmis_satisfaction")
public class SatisfactionDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务编码（SV-YYYYMMDD-XXXX） */
    private String surveyCode;
    private Long initiationId;
    private Long ticketId;
    private Long warrantyId;
    /** 总体评分 1-5 */
    private Integer score;
    /** SatisfactionLevel.code */
    private String level;
    private Integer professionalism;
    private Integer timeliness;
    private Integer quality;
    private Integer attitude;
    private String comments;
    private String suggest;
    private Boolean anonymous;
    private Long evaluatorId;
    private String evaluatorName;
    private LocalDateTime evaluatedAt;
    private Boolean followUp;
    private String followUpNote;
    private Long tenantId;
    private String providerTraceId;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
