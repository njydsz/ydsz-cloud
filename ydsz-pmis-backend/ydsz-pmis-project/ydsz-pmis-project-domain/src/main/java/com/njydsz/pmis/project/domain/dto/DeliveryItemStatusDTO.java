package com.njydsz.pmis.project.domain.dto;

import java.io.Serial;
import java.io.Serializable;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Data;

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

    @NotNull(message = "{validation.execution.msg_da609842}")
    private String id;

    @NotBlank(message = "{validation.execution.msg_8304cf7d}")
    private String targetStatus;

    private String reviewComment;
    private String reviewerId;
    private String reviewerName;
}
