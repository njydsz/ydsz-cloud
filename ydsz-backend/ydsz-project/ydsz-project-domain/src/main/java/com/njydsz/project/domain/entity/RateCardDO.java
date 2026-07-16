package com.njydsz.project.domain.entity;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 对外报价费率（Rate Card）
 *
 * <p>按 (职级 × 项目类型 × 客户等级) 三维度定义每日/每小时报价。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@TableName("ydsz_rate_card")
public class RateCardDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 业务编号 */
    private String rateCode;
    /** 职级 L1-L18 */
    private String levelCode;
    /** 项目类型：ProjectType.code */
    private String projectType;
    /** 客户等级：A/B/C/D */
    private String customerLevel;
    /** 计费单位：DAY/HOUR */
    private String billingUnit;
    /** 报价金额 */
    private BigDecimal rateAmount;
    /** 币种：CNY/USD/EUR */
    private String currency;
    /** 生效日期 */
    private LocalDate effectiveDate;
    /** 失效日期 */
    private LocalDate expiryDate;
    /** 状态：ACTIVE/INACTIVE */
    private String status;
    /** 备注 */
    private String remark;
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
}
