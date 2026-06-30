package com.njydsz.pmis.project.dto;

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

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "模板编码不能为空")
    private String templateCode;

    @NotBlank(message = "模板名称不能为空")
    private String templateName;

    @NotBlank(message = "合同类型不能为空")
    private String contractType;

    private String version;
    private String paymentTerms;
    private Integer defaultPaymentDays;
    private BigDecimal defaultPenaltyRate;
    private String slaDescription;
    private String deliverables;
    private String content;
    private String customerLevel;
    private String projectLevel;
    private String status;
    private Long authorId;
    private String remark;
    private Long tenantId;
}
