package com.njydsz.pmis.execution.dto;

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

    @NotBlank(message = "项目类型不能为空")
    private String projectType;

    private String projectLevel;

    @NotBlank(message = "交付物名称不能为空")
    private String deliveryName;

    private String deliveryCategory;

    @NotBlank(message = "所属阶段不能为空")
    private String stage;

    private Integer required;
    private Integer triggerTr;
    private String acceptanceCriteria;
    private String templateRef;
    private String remark;
    private Long tenantId;
}
