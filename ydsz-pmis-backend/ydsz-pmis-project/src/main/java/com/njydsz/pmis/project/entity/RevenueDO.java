package com.njydsz.pmis.project.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 收入确认
 *
 * <p>按里程碑/完工百分比/进度比例/人天点数/手工确认等方式记录项目收入。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@TableName("pmis_profit_revenue")
public class RevenueDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 合同ID */
    private String contractId;
    /** 项目立项ID */
    private String initiationId;
    /** 收入编号 */
    private String revenueCode;
    /** 收入确认方法：RevenueRecognitionMethod.code */
    private String recognitionMethod;
    /** 所属期间（YYYY-MM） */
    private String period;
    /** 确认金额 */
    private BigDecimal amount;
    /** 确认日期 */
    private LocalDate recognitionDate;
    /** 关联里程碑 */
    private String milestone;
    /** 完工百分比（0-1） */
    private BigDecimal percentComplete;
    /** 关联发票ID */
    private String invoiceId;
    /** 状态 */
    private String status;
    /** 确认人ID */
    private String confirmedBy;
    /** 确认时间 */
    private LocalDateTime confirmedAt;
    /** 描述 */
    private String description;
    /** 租户ID */
    private String tenantId;
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

    /** 乐观锁版本号（P1-2） */
    @Version
    private Integer version;
}
