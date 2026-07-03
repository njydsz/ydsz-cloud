package com.njydsz.pmis.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 项目结项状态迁移 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class ProjectClosureStatusDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "{validation.execution.msg_c9491140}")
    private Long id;

    @NotBlank(message = "{validation.execution.msg_8304cf7d}")
    private String targetStatus;

    private String approvalComment;
    private Long approverId;
    private String approverName;
}
