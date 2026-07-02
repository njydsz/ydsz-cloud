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

    @NotBlank(message = "{validation.execution.msg_1fd28961}")
    private String itemCode;

    @NotNull(message = "{validation.execution.msg_576c2b5e}")
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
