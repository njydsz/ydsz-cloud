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
 * 发票主表
 *
 * <p>支持正常开票与红冲发票；记录开票依据（里程碑/外包/终验等）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@TableName("pmis_finance_invoice")
public class InvoiceDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String invoiceNo;             // 发票号（系统/财务）
    private String invoiceCode;           // 业务编号（系统生成）
    private String invoiceType;           // NORMAL/RED_REVERSE
    private Long contractId;
    private Long initiationId;
    private Long customerId;
    private String customerName;
    private String invoiceBasis;          // MILESTONE/OUTSOURCING/MONTHLY/FINAL/OTHER
    private BigDecimal amount;            // 含税金额
    private BigDecimal taxAmount;         // 税额
    private BigDecimal netAmount;         // 不含税金额
    private BigDecimal taxRate;           // 税率
    private String currency;              // CNY/USD/EUR
    private LocalDate invoiceDate;
    private LocalDate taxPeriod;          // 税务所属期（YYYY-MM）
    private String title;                 // 发票抬头
    private String taxNo;                 // 纳税人识别号
    private String bankInfo;              // 开户行+账号
    private String address;               // 公司地址
    private String phone;                 // 公司电话
    private String remark;
    private String status;                // InvoiceStatus.code
    private Long reversedById;            // 被红冲的发票ID
    private String attachmentId;          // 发票扫描件/电子发票文件ID
    private String approvalComment;
    private Long appliedBy;
    private Long approvedBy;
    private LocalDateTime approvedAt;
    private Long issuedBy;
    private LocalDateTime issuedAt;
    private Long tenantId;
    private String providerTraceId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
