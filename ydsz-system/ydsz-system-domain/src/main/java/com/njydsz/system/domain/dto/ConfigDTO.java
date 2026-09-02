package com.njydsz.system.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.safe.annotation.Xss;

/**
 * 系统配置创建/更新 DTO
 *
 * <p>对应 {@code ydsz_sys_config} 表的写入参数，是「系统配置中心」创建 / 更新接口的入参载体。 创建时 {@code id} 为空（由雪花算法自动生成），更新时
 * {@code id} 必填。
 *
 * <p><b>P1-5 字段重叠处理：</b>本类与 {@link ConfigVO} 字段完全一致，遵循《云顶编码规范》"VO 兼 DTO"模式（字段无差异时不作三分离）。 作为
 * Repository 层的写入契约存在，避免 infra 层直接依赖 {@link ConfigVO}（保持分层边界清晰）。
 *
 * <p><b>维护约定：</b>新增字段时需同步 {@link ConfigVO}，确保两者结构一致。
 *
 * <p><b>字段语义：</b>
 *
 * <ul>
 *   <li>{@code configGroup} — 配置分组，按业务域分类管理
 *   <li>{@code configKey} — 配置键，同组内唯一标识
 *   <li>{@code configValue} — 配置值
 *   <li>{@code valueType} — 值类型: STRING/NUMBER/BOOLEAN/JSON
 *   <li>{@code defaultValue} — 默认值（配置未设置时使用）
 *   <li>{@code isPublic} — 是否对前端公开: 1 公开 / 0 仅后端
 *   <li>{@code sortOrder} — 排序序号
 *   <li>{@code status} — 启用状态: ENABLED/DISABLED
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see ConfigVO 字段完全一致的视图对象（"VO 兼 DTO"模式）
 */
@Data
@SuperBuilder
@NoArgsConstructor
public class ConfigDTO {

  private String id;

  @NotBlank(message = "配置分组不能为空")
  @Size(max = 64, message = "配置分组长度不能超过64")
  @Xss(message = "配置分组包含非法内容")
  private String configGroup;

  @NotBlank(message = "配置键不能为空")
  @Size(max = 128, message = "配置键长度不能超过128")
  @Xss(message = "配置键包含非法内容")
  private String configKey;

  @Xss(message = "配置值包含非法内容")
  private String configValue;

  @NotBlank(message = "值类型不能为空")
  private String valueType;

  @Xss(message = "默认值包含非法内容")
  private String defaultValue;

  @Xss(message = "配置项说明包含非法内容")
  private String description;

  private Integer isPublic;

  private Integer sortOrder;

  private String status;
}
