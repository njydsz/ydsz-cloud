package com.njydsz.pmis.sales.entity.aftersales;

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

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 业务编码（SV-YYYYMMDD-XXXX） */
    private String surveyCode;
    /** 项目立项ID */
    private String initiationId;
    /** 关联工单ID（可空） */
    private String ticketId;
    /** 关联质保单ID（可空） */
    private String warrantyId;
    /** 总体评分 1-5 */
    private Integer score;
    /** SatisfactionLevel.code */
    private String level;
    /** 专业度评分 1-5 */
    private Integer professionalism;
    /** 及时性评分 1-5 */
    private Integer timeliness;
    /** 质量评分 1-5 */
    private Integer quality;
    /** 服务态度评分 1-5 */
    private Integer attitude;
    /** 评价意见 */
    private String comments;
    /** 改进建议 */
    private String suggest;
    /** 是否匿名评价 */
    private Boolean anonymous;
    /** 评价人ID */
    private String evaluatorId;
    /** 评价人姓名 */
    private String evaluatorName;
    /** 评价时间 */
    private LocalDateTime evaluatedAt;
    /** 是否需要回访 */
    private Boolean followUp;
    /** 回访记录 */
    private String followUpNote;
    /** 租户ID */
    private String tenantId;
    /** 链路追踪ID */
    private String providerTraceId;

    /** 创建人ID */
    @TableField(fill = FieldFill.INSERT)
    private String createdBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新人ID */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除标志：1 已删除 / 0 未删除 */
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
