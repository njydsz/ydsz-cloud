package com.njydsz.project.domain.entity;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 项目交付物实例
 *
 * <p>每个项目每个交付物一条记录，记录提交时间、验收状态、附件。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@TableName("ydsz_execution_delivery_item")
public class DeliveryItemDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 交付物业务编号 */
    private String itemCode;
    /** 项目立项ID */
    private String initiationId;
    /** 关联交付物标准ID */
    private String standardId;
    /** 项目类型：ProjectType.code */
    private String projectType;
    /** 项目等级 */
    private String projectLevel;
    /** 交付物名称 */
    private String deliveryName;
    /** 交付物分类 */
    private String deliveryCategory;
    /** 所属门径阶段：DeliveryStage.code */
    private String stage;
    /** 是否必交付：1 是 / 0 否 */
    private Integer required;
    /** 计划提交日期 */
    private LocalDate plannedSubmitDate;
    /** 实际提交日期 */
    private LocalDate actualSubmitDate;
    /** 验收日期 */
    private LocalDate acceptedDate;
    /** 提交人ID */
    private String submitterId;
    /** 提交人姓名 */
    private String submitterName;
    /** 评审人ID */
    private String reviewerId;
    /** 评审人姓名 */
    private String reviewerName;
    /** 评审意见 */
    private String reviewComment;
    /** 状态：DeliveryItemStatus.code */
    private String status;
    /** 是否触发技术评审 TR：1 是 / 0 否 */
    private Integer trRequired;
    /** TR 是否完成：1 是 / 0 否 */
    private Integer trCompleted;
    /** 附件 ID 列表（JSON 数组） */
    private String fileIds;
    /** 备注 */
    private String remark;
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
