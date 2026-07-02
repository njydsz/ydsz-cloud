package com.njydsz.pmis.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 合同模板状态迁移 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class ContractTemplateStatusDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 模板 ID */
    @NotNull(message = "模板 ID 不能为空")
    private Long id;

    /** 目标状态（ContractTemplateStatus.code） */
    @NotBlank(message = "目标状态不能为空")
    private String targetStatus;
}
