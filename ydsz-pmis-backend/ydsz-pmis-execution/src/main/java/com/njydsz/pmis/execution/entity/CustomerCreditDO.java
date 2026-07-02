package com.njydsz.pmis.execution.entity;

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
import java.time.LocalDateTime;

/**
 * 客户信用记录
 *
 * <p>按客户维度跟踪：累计合同金额、累计回款、回款及时率、当前等级。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@TableName("pmis_finance_customer_credit")
public class CustomerCreditDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 客户ID */
    private Long customerId;
    /** 客户名称 */
    private String customerName;
    /** 信用等级：CreditLevel.code (A/B/C/D) */
    private String creditLevel;
    /** 信用评分（0-100） */
    private Integer creditScore;
    /** 累计合同金额 */
    private BigDecimal totalContractAmount;
    /** 累计开票金额 */
    private BigDecimal totalInvoicedAmount;
    /** 累计回款金额 */
    private BigDecimal totalReceivedAmount;
    /** 及时回款率（0-1） */
    private BigDecimal onTimeRate;
    /** 合作合同数 */
    private Integer contractCount;
    /** 逾期次数 */
    private Integer overdueCount;
    /** 上次评估时间 */
    private LocalDateTime lastEvaluationAt;
    /** 评估人 */
    private String evaluator;
    /** 备注 */
    private String remark;
    /** 租户ID */
    private Long tenantId;
    /** 链路追踪ID */
    private String providerTraceId;

    /** 乐观锁版本号（P1-12） */
    @Version
    private Integer version;

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
