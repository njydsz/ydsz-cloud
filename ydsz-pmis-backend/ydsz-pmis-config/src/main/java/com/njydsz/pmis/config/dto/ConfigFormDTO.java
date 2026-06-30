package com.njydsz.pmis.config.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 配置表单 DTO
 */
@Data
@Schema(description = "配置表单")
public class ConfigFormDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    @NotBlank
    private String configGroup;

    @NotBlank
    private String configKey;

    private String configValue;

    private String defaultValue;

    /** STRING/NUMBER/BOOLEAN/JSON */
    private String valueType = "STRING";

    private String description;

    private Integer isPublic = 0;

    private Integer sortOrder = 0;

    private String status = "ENABLED";
}
