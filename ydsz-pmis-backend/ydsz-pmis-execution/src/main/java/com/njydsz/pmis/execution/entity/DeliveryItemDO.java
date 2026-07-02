package com.njydsz.pmis.execution.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 项目交付物实例
 *
 * <p>每个项目每个交付物一条记录，记录提交时间、验收状态、附件。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@TableName("pmis_execution_delivery_item")
public class DeliveryItemDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String itemCode;
    private Long initiationId;
    /** 关联交付物标准ID */
    private Long standardId;
    private String projectType;
    private String projectLevel;
    private String deliveryName;
    private String deliveryCategory;
    /** 所属门径阶段：DeliveryStage.code */
    private String stage;
    /** 是否必交付：1 是 / 0 否 */
    private Integer required;
    private LocalDate plannedSubmitDate;
    private LocalDate actualSubmitDate;
    private LocalDate acceptedDate;
    private Long submitterId;
    private String submitterName;
    private Long reviewerId;
    private String reviewerName;
    private String reviewComment;
    /** 状态：DeliveryItemStatus.code */
    private String status;
    /** 是否触发技术评审 TR：1 是 / 0 否 */
    private Integer trRequired;
    /** TR 是否完成：1 是 / 0 否 */
    private Integer trCompleted;
    /** 附件 ID 列表（JSON 数组） */
    private String fileIds;
    private String remark;
    private Long tenantId;
    private String providerTraceId;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除标志：1 已删除 / 0 未删除 */
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
