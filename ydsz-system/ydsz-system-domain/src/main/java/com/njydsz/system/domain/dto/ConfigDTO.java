package com.njydsz.system.domain.dto;

import com.njydsz.common.safe.annotation.Xss;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 系统配置创建/更新 DTO
 *
 * <p>对应 {@code ydsz_config} 表的写入参数，承担「系统配置中心」的配置项 CRUD 入参。
 *
 * <p><b>字段约束：</b>
 *
 * <ul>
 *   <li>{@code configGroup} — 配置分组（如 {@code ydsz.workflow} / {@code ydsz.message}），最长 64 字符
 *   <li>{@code configKey} — 配置键（分组内唯一），最长 128 字符
 *   <li>{@code configValue} — 配置值（按 {@code valueType} 解析）
 *   <li>{@code valueType} — 值类型：{@code STRING / NUMBER / BOOLEAN / JSON}
 *   <li>{@code defaultValue} — 默认值（配置未设置时回退）
 *   <li>{@code isPublic} — 是否对前端公开：{@code 1} 公开 / {@code 0} 仅后端
 *   <li>{@code sortOrder} — 同分组内排序号（升序）
 *   <li>{@code status} — 启用状态：{@code ENABLED / DISABLED}
 * </ul>
 *
 * <p><b>唯一约束：</b>（{@code tenantId}, {@code configGroup}, {@code configKey}）三联唯一。
 *
 * <p><b>缓存策略：</b>配置项读取时通过 {@code ydsz:config:{group}:{key}} 缓存至 Redis， 写入时 {@code @CacheEvict}
 * 主动失效。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.system.domain.entity.Config 系统配置实体
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
  @Xss(message = "配置分组包含非法内容")
  @Schema(description = "配置分组")
  private String configGroup;

  @NotBlank(message = "配置键不能为空")
  @Size(max = 128, message = "配置键长度不能超过128")
  @Xss(message = "配置键包含非法内容")
  @Schema(description = "配置键")
  private String configKey;

  @Xss(message = "配置值包含非法内容")
  @Schema(description = "配置值")
  private String configValue;

  @NotBlank(message = "值类型不能为空")
  @Schema(description = "值类型: STRING/NUMBER/BOOLEAN/JSON")
  private String valueType;

  @Xss(message = "默认值包含非法内容")
  @Schema(description = "默认值")
  private String defaultValue;

  @Xss(message = "配置项说明包含非法内容")
  @Schema(description = "配置项说明")
  private String description;

  @Schema(description = "是否对前端公开: 1 公开 / 0 仅后端")
  private Integer isPublic;

  @Schema(description = "排序号")
  private Integer sortOrder;

  @Schema(description = "启用状态: ENABLED/DISABLED")
  private String status;
}
