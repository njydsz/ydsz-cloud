package com.njydsz.pmis.project.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.njydsz.pmis.common.sensitive.Sensitive;
import com.njydsz.pmis.common.sensitive.SensitiveStrategy;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 回款记录
 *
 * <p>支持按客户回款、按合同核销；可与一张或多张发票自动匹配核销。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@TableName("pmis_finance_payment")
public class PaymentDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 流水号 */
    private String paymentNo;
    /** 业务编号 */
    private String paymentCode;
    /** 合同ID */
    private String contractId;
    /** 项目立项ID */
    private String initiationId;
    /** 客户ID */
    private String customerId;
    /** 客户名称 */
    private String customerName;
    /** 回款金额 */
    private BigDecimal amount;
    /** 币种 */
    private String currency;
    /** 付款方式：BANK_TRANSFER/CHECK/CASH/OTHER */
    private String paymentMethod;
    /** 到账日期 */
    private LocalDate paymentDate;
    /** 客户付款账户（脱敏：保留前 4 后 4） */
    @Sensitive(SensitiveStrategy.BANK_CARD)
    private String bankAccount;
    /** 我方收款账户（脱敏：保留前 4 后 4） */
    @Sensitive(SensitiveStrategy.BANK_CARD)
    private String ourBankAccount;
    /** 银行流水号 */
    private String bankReference;
    /** 已分配发票ID列表（JSON/逗号分隔） */
    private String invoiceAllocation;
    /** 已核销金额 */
    private BigDecimal allocatedAmount;
    /** 未核销金额 */
    private BigDecimal unallocatedAmount;
    /** 状态：PaymentStatus.code */
    private String status;
    /** 备注 */
    private String remark;
    /** 确认人ID */
    private Long confirmedBy;
    /** 确认时间 */
    private LocalDateTime confirmedAt;
    /** 录入人ID */
    private Long recordedBy;
    /** 租户ID */
    private String tenantId;
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
