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
 * 合同模板
 *
 * <p>用于 8 类项目类型的标准合同模板（条款、付款方式、SLA 等）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@TableName("pmis_project_contract_template")
public class ContractTemplateDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 模板编码（业务唯一） */
    private String templateCode;
    /** 模板名称 */
    private String templateName;
    /** 合同类型（ContractTemplateType.code） */
    private String contractType;
    /** 版本号，例如 1.0.0 */
    private String version;
    /** 标准付款条款 */
    private String paymentTerms;
    /** 标准账期（天） */
    private Integer defaultPaymentDays;
    /** 违约金比例（0-1） */
    private java.math.BigDecimal defaultPenaltyRate;
    /** SLA 描述（多行） */
    private String slaDescription;
    /** 交付物清单（多行） */
    private String deliverables;
    /** 模板正文（条款/正文内容） */
    private String content;
    /** 适用客户等级（A/B/C/D） */
    private String customerLevel;
    /** 适用项目级别（L1-L18） */
    private String projectLevel;
    /** 状态：DRAFT/PUBLISHED/DEPRECATED */
    private String status;
    /** 模板作者 ID */
    private Long authorId;
    /** 模板作者姓名（冗余） */
    private String authorName;
    /** 备注 */
    private String remark;
    private Long tenantId;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
