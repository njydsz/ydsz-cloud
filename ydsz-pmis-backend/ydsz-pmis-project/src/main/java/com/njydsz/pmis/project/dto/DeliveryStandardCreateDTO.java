package com.njydsz.pmis.project.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 交付物标准 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class DeliveryStandardCreateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "{validation.execution.msg_40dfe929}")
    private String projectType;

    private String projectLevel;

    @NotBlank(message = "{validation.execution.msg_ddf1cbe9}")
    private String deliveryName;

    private String deliveryCategory;

    @NotBlank(message = "{validation.execution.msg_4819a855}")
    private String stage;

    private Integer required;
    private Integer triggerTr;
    private String acceptanceCriteria;
    private String templateRef;
    private String remark;
    private String tenantId;
}
