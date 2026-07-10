package com.njydsz.pmis.system.dto.config;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 配置表单 DTO。
 *
 * <p>用于配置项的创建和更新操作，对应 {@code pmis_config} 表的写入场景。
 * 创建时 {@code id} 为空，更新时必填；{@code configGroup} 和 {@code configKey} 为必填字段。
 *
 * <p>配置类型支持 STRING / NUMBER / BOOLEAN / JSON，通过 {@code valueType} 区分。
 * 公开配置（{@code isPublic=1}）可被前端直接读取，私有配置（{@code isPublic=0}）仅后端使用。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Schema(description = "配置表单")
public class ConfigFormDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 配置 ID（更新时必填，创建时为空） */
    private String id;

    /** 配置分组（如 system / business / workflow） */
    @NotBlank
    private String configGroup;

    /** 配置键（分组内唯一，如 sys.title、biz.retry.count） */
    @NotBlank
    private String configKey;

    /** 配置值（STRING 类型为原始值，JSON 类型为 JSON 字符串） */
    private String configValue;

    /** 默认值（配置项未设置时使用的兜底值） */
    private String defaultValue;

    /** 值类型：STRING / NUMBER / BOOLEAN / JSON */
    private String valueType = "STRING";

    /** 配置描述（用途说明，便于运维理解） */
    private String description;

    /** 1=前端可见（public），0=私有 */
    private Integer isPublic = 0;

    /** 排序序号（同分组内按 sortOrder 升序展示） */
    private Integer sortOrder = 0;

    /** 状态：ENABLED / DISABLED */
    private String status = "ENABLED";
}
