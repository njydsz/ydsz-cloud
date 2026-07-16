package com.njydsz.project.domain.dto;

import java.io.Serial;
import java.io.Serializable;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Data;

/**
 * 项目变更状态迁移 DTO
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class ProjectChangeStatusDTO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 变更 ID */
    @NotNull(message = "{validation.project.msg_ad21f8c7}")
    private String id;

    /** 目标状态（ChangeStatus.code） */
    @NotBlank(message = "{validation.project.msg_8304cf7d}")
    private String targetStatus;
}
