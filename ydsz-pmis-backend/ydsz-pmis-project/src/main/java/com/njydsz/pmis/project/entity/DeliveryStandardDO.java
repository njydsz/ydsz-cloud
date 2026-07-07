package com.njydsz.pmis.project.entity;

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
 * 项目交付物标准
 *
 * <p>8 类项目类型对应的标准交付物清单（每个阶段应交付的产物）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@TableName("pmis_execution_delivery_standard")
public class DeliveryStandardDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 项目类型（ProjectType.code） */
    private String projectType;
    /** 项目级别 L1-L18 */
    private String projectLevel;
    /** 交付物名称 */
    private String deliveryName;
    /** 交付物类别（DOC/CODE/MODEL/RUNBOOK/REPORT/OTHER） */
    private String deliveryCategory;
    /** 所属门径阶段（DeliveryStage.code） */
    private String stage;
    /** 是否必交付（1=必交付，0=可豁免） */
    private Integer required;
    /** 是否触发技术评审 TR（高级项目） */
    private Integer triggerTr;
    /** 验收标准 */
    private String acceptanceCriteria;
    /** 模板 ID/链接（可选） */
    private String templateRef;
    /** 备注 */
    private String remark;
    /** 租户ID */
    private Long tenantId;
    /** 链路追踪ID */
    private String providerTraceId;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除标志：1 已删除 / 0 未删除 */
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
