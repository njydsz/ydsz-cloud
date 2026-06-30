package com.njydsz.pmis.execution.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 对外报价费率（Rate Card）
 *
 * <p>按 (职级 × 项目类型 × 客户等级) 三维度定义每日/每小时报价。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@TableName("pmis_rate_card")
public class RateCardDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String rateCode;           // 业务编号
    private String levelCode;          // 职级 L1-L18
    private String projectType;        // ProjectType.code
    private String customerLevel;      // A/B/C/D
    private String billingUnit;        // DAY/HOUR
    private BigDecimal rateAmount;     // 报价金额
    private String currency;           // CNY/USD/EUR
    private LocalDate effectiveDate;   // 生效日期
    private LocalDate expiryDate;      // 失效日期
    private String status;             // ACTIVE/INACTIVE
    private String remark;
    private Long tenantId;
    private String providerTraceId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
