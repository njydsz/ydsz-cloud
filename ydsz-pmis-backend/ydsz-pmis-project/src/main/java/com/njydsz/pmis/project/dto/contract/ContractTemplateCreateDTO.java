package com.njydsz.pmis.project.dto.contract;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 合同模板创建 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class ContractTemplateCreateDTO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 模板编码（业务唯一） */
    @NotBlank(message = "{validation.project.msg_89695105}")
    private String templateCode;

    /** 模板名称 */
    @NotBlank(message = "{validation.project.msg_a23746e5}")
    private String templateName;

    /** 合同类型（ContractTemplateType.code） */
    @NotBlank(message = "{validation.project.msg_fc52e1b0}")
    private String contractType;

    /** 版本号 */
    private String version;
    /** 标准付款条款 */
    private String paymentTerms;
    /** 标准账期（天） */
    private Integer defaultPaymentDays;
    /** 违约金比例（0-1） */
    private BigDecimal defaultPenaltyRate;
    /** SLA 描述 */
    private String slaDescription;
    /** 交付物清单 */
    private String deliverables;
    /** 模板正文 */
    private String content;
    /** 适用客户等级 */
    private String customerLevel;
    /** 适用项目级别 */
    private String projectLevel;
    /** 状态（ContractTemplateStatus.code） */
    private String status;
    /** 模板作者 ID */
    private String authorId;
    /** 备注 */
    private String remark;
    /** 租户 ID */
    private String tenantId;
}
