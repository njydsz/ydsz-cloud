package com.remisoft.system.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 系统配置创建/更新 DTO
 *
 * <p>对应 {@code remi_config} 表的写入参数，承担「系统配置中心」的配置项 CRUD 入参。
 *
 * <p><b>字段约束：</b>
 * <ul>
 *   <li>{@code configGroup} — 配置分组（如 {@code remi.workflow} / {@code remi.message}），最长 64 字符</li>
 *   <li>{@code configKey} — 配置键（分组内唯一），最长 128 字符</li>
 *   <li>{@code configValue} — 配置值（按 {@code valueType} 解析）</li>
 *   <li>{@code valueType} — 值类型：{@code STRING / NUMBER / BOOLEAN / JSON}</li>
 *   <li>{@code defaultValue} — 默认值（配置未设置时回退）</li>
 *   <li>{@code isPublic} — 是否对前端公开：{@code 1} 公开 / {@code 0} 仅后端</li>
 *   <li>{@code sortOrder} — 同分组内排序号（升序）</li>
 *   <li>{@code status} — 启用状态：{@code ENABLED / DISABLED}</li>
 * </ul>
 *
 * <p><b>唯一约束：</b>（{@code tenantId}, {@code configGroup}, {@code configKey}）三联唯一。
 *
 * <p><b>缓存策略：</b>配置项读取时通过 {@code remi:config:{group}:{key}} 缓存至 Redis，
 * 写入时 {@code @CacheEvict} 主动失效。
 *
 * @author remi-team
 * @since 1.0.0
 *
 * @see com.remisoft.system.domain.entity.Config 系统配置实体
 */
@Data
@SuperBuilder
@NoArgsConstructor
@Schema(description = "系统配置创建/更新 DTO")
public class ConfigDTO {

    @Schema(description = "主键 ID（更新时必填）")
    private String id;

    @NotBlank(message = "配置分组不能为空")
    @Size(max = 64, message = "配置分组长度不能超过64")
    @Schema(description = "配置分组")
    private String configGroup;

    @NotBlank(message = "配置键不能为空")
    @Size(max = 128, message = "配置键长度不能超过128")
    @Schema(description = "配置键")
    private String configKey;

    @Schema(description = "配置值")
    private String configValue;

    @NotBlank(message = "值类型不能为空")
    @Schema(description = "值类型: STRING/NUMBER/BOOLEAN/JSON")
    private String valueType;

    @Schema(description = "默认值")
    private String defaultValue;

    @Schema(description = "配置项说明")
    private String description;

    @Schema(description = "是否对前端公开: 1 公开 / 0 仅后端")
    private Integer isPublic;

    @Schema(description = "排序号")
    private Integer sortOrder;

    @Schema(description = "启用状态: ENABLED/DISABLED")
    private String status;
}
