package com.njydsz.pmis.project.domain.dto;

import java.io.Serial;
import java.io.Serializable;

import jakarta.validation.constraints.NotBlank;

import lombok.Data;

/**
 * 合同模板状态迁移 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class ContractTemplateStatusDTO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 模板 ID */
    @NotBlank(message = "{validation.project.msg_ff1828c0}")
    private String id;

    /** 目标状态（ContractTemplateStatus.code） */
    @NotBlank(message = "{validation.project.msg_8304cf7d}")
    private String targetStatus;
}
