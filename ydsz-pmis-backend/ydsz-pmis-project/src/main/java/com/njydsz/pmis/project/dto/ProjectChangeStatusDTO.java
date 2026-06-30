package com.njydsz.pmis.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 项目变更状态迁移 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class ProjectChangeStatusDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "变更 ID 不能为空")
    private Long id;

    @NotBlank(message = "目标状态不能为空")
    private String targetStatus;
}
