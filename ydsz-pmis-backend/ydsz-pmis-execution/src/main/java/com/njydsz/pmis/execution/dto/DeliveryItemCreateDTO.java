package com.njydsz.pmis.execution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 交付物实例 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class DeliveryItemCreateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "交付物编码不能为空")
    private String itemCode;

    @NotNull(message = "项目 ID 不能为空")
    private Long initiationId;

    private Long standardId;
    private String projectType;
    private String projectLevel;
    private String deliveryName;
    private String deliveryCategory;
    private String stage;
    private Integer required;
    private LocalDate plannedSubmitDate;
    private Long submitterId;
    private String submitterName;
    private Integer trRequired;
    private String fileIds;
    private String remark;
    private Long tenantId;
}
