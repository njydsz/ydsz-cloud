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

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 发票号（系统/财务） */
    private String invoiceNo;
    /** 业务编号（系统生成） */
    private String invoiceCode;
    /** 发票类型：NORMAL/RED_REVERSE */
    private String invoiceType;
    /** 合同ID */
    private Long contractId;
    /** 项目立项ID */
    private Long initiationId;
    /** 客户ID */
    private Long customerId;
    /** 客户名称 */
    private String customerName;
    /** 开票依据：MILESTONE/OUTSOURCING/MONTHLY/FINAL/OTHER */
    private String invoiceBasis;
    /** 含税金额 */
    private BigDecimal amount;
    /** 税额 */
    private BigDecimal taxAmount;
    /** 不含税金额 */
    private BigDecimal netAmount;
    /** 税率 */
    private BigDecimal taxRate;
    /** 币种：CNY/USD/EUR */
    private String currency;
    /** 开票日期 */
    private LocalDate invoiceDate;
    /** 税务所属期（YYYY-MM） */
    private LocalDate taxPeriod;
    /** 发票抬头 */
    private String title;
    /** 纳税人识别号 */
    private String taxNo;
    /** 开户行+账号 */
    private String bankInfo;
    /** 公司地址 */
    private String address;
    /** 公司电话 */
    private String phone;
    /** 备注 */
    private String remark;
    /** 状态：InvoiceStatus.code */
    private String status;
    /** 被红冲的发票ID */
    private Long reversedById;
    /** 发票扫描件/电子发票文件ID */
    private String attachmentId;
    /** 审批意见 */
    private String approvalComment;
    /** 申请人ID */
    private Long appliedBy;
    /** 审批人ID */
    private Long approvedBy;
    /** 审批时间 */
    private LocalDateTime approvedAt;
    /** 开票人ID */
    private Long issuedBy;
    /** 开票时间 */
    private LocalDateTime issuedAt;
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
