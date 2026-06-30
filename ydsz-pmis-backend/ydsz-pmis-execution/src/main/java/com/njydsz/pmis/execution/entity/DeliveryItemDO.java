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
    private Long standardId;            // 关联交付物标准
    private String projectType;
    private String projectLevel;
    private String deliveryName;
    private String deliveryCategory;
    private String stage;                // DeliveryStage.code
    private Integer required;            // 1=必交付
    private LocalDate plannedSubmitDate;
    private LocalDate actualSubmitDate;
    private LocalDate acceptedDate;
    private Long submitterId;
    private String submitterName;
    private Long reviewerId;
    private String reviewerName;
    private String reviewComment;
    private String status;               // DeliveryItemStatus.code
    private Integer trRequired;          // 是否触发 TR
    private Integer trCompleted;         // TR 是否完成
    private String fileIds;              // 附件 ID 列表（JSON 数组）
    private String remark;
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
