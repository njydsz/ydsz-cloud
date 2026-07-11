package com.njydsz.pmis.sales.domain.entity;

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
 * 合同主表
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@TableName("pmis_project_contract")
public class ContractDO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 合同编号 */
    private String contractCode;
    /** 合同名称 */
    private String contractName;
    /** 关联立项 ID */
    private String initiationId;
    /** 客户 ID */
    private String customerId;
    /** 客户名称 */
    private String customerName;
    /** 合同类型（FIXED_PRICE/T&M/OUTSOURCING/PRODUCT/MAINTENANCE） */
    private String contractType;
    /** 签订日期 */
    private LocalDate signDate;
    /** 生效日期 */
    private LocalDate effectiveDate;
    /** 到期日期 */
    private LocalDate expireDate;
    /** 合同总金额 */
    private BigDecimal totalAmount;
    /** 币种 */
    private String currency;
    /** 付款条款 */
    private String paymentTerms;
    /** 结算周期 */
    private String billingCycle;
    /** 税率 */
    private BigDecimal taxRate;
    /** 合同状态（ContractStatus.code） */
    private String status;
    /** 风险等级（LOW/MEDIUM/HIGH） */
    private String riskLevel;
    /** 风险说明 */
    private String riskNotes;
    /** 责任人 ID */
    private String ownerId;
    /** 责任人名称（脱敏：保留首末字） */
    @Sensitive(SensitiveStrategy.NAME)
    private String ownerName;
    /** 合同附件 ID */
    private String contractFileId;
    /** 自研工作流实例 ID */
    private String workflowId;
    /** 备注 */
    private String remark;
    /** 租户 ID */
    private String tenantId;

    /**
     * 乐观锁版本号（P1-12）
     *
     * <p>合同金额、状态等关键字段并发更新时，通过 version 防止覆盖。
     */
    @Version
    private Integer version;

    /** 创建人 ID */
    @TableField(fill = FieldFill.INSERT)
    private String createdBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新人 ID */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除标识（0 未删除，1 已删除） */
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
