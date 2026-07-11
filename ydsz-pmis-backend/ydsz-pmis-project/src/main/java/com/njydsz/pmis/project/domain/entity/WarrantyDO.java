package com.njydsz.pmis.project.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.sensitive.Sensitive;
import com.njydsz.pmis.common.sensitive.SensitiveStrategy;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 质保期实体
 *
 * <p>项目结项后自动创建，到期前 N 天提醒，到期后自动 EXPIRED。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@TableName("pmis_warranty")
public class WarrantyDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 业务编码（WY-YYYYMMDD-XXXX） */
    private String warrantyCode;
    /** 项目立项ID */
    private String initiationId;
    /** 合同ID */
    private String contractId;
    /** 项目类型：ProjectType.code */
    private String projectType;
    /** 项目等级 */
    private String projectLevel;
    /** 质保期开始日期 */
    private LocalDate startDate;
    /** 质保期结束日期 */
    private LocalDate endDate;
    /** 质保期月数 */
    private Integer durationMonths;
    /** 到期前提醒天数 */
    private Integer noticeDays;
    /** 是否已发送到期提醒 */
    private Boolean noticeSent;
    /** 提醒发送时间 */
    private LocalDateTime noticeSentAt;
    /** WarrantyStatus.code */
    private String status;
    /** 终止时间 */
    private LocalDateTime terminatedAt;
    /** 终止原因 */
    private String terminatedReason;
    /** 联系人姓名 */
    private String contactName;
    /** 联系人电话（脱敏：138****8000） */
    @Sensitive(SensitiveStrategy.PHONE)
    private String contactPhone;
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
