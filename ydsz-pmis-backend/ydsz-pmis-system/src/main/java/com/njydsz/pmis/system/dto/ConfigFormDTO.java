package com.njydsz.pmis.system.dto;

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
    private static final String serialVersionUID = "1";

    /** 配置 ID（更新时必填） */
    private String id;

    /** 配置分组 */
    @NotBlank
    private String configGroup;

    /** 配置键 */
    @NotBlank
    private String configKey;

    /** 配置值 */
    private String configValue;

    /** 默认值 */
    private String defaultValue;

    /** STRING/NUMBER/BOOLEAN/JSON */
    private String valueType = "STRING";

    /** 配置描述 */
    private String description;

    /** 1=前端可见（public），0=私有 */
    private Integer isPublic = 0;

    /** 排序序号 */
    private Integer sortOrder = 0;

    /** 状态：ENABLED/DISABLED */
    private String status = "ENABLED";
}
