package com.njydsz.pmis.execution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 交付物状态迁移 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class DeliveryItemStatusDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "交付物 ID 不能为空")
    private Long id;

    @NotBlank(message = "目标状态不能为空")
    private String targetStatus;

    private String reviewComment;
    private Long reviewerId;
    private String reviewerName;
}
