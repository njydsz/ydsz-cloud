package com.njydsz.system.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.safe.annotation.Xss;

/**
 * 系统配置 VO（兼 DTO）
 *
 * <p>对应 {@code ydsz_config} 表的展示视图和写入参数，是「系统配置中心」列表 / 详情 / 创建 / 更新接口的通用载体。 由 {@link
 * com.njydsz.system.domain.converter.SystemConverter} 从 {@link
 * com.njydsz.system.domain.entity.Config} 实体转换而来。
 *
 * <p><b>字段语义：</b>
 *
 * <ul>
 *   <li>{@code configValue} — 配置值，序列化时按 {@code valueType} 转换： {@code STRING} 原样输出；{@code NUMBER /
 *       BOOLEAN / JSON} 解析为对应类型
 *   <li>{@code defaultValue} — 默认值（{@code configValue} 为空时回退）
 *   <li>{@code isPublic} — 是否对前端公开：{@code 1} 公开 / {@code 0} 仅后端； 前端「公开配置」接口仅返回 {@code isPublic=1}
 *       的项
 * </ul>
 *
 * <p><b>使用场景：</b>
 *
 * <ul>
 *   <li>配置中心列表 / 详情 / 编辑回显
 *   <li>业务参数动态下发（{@code ydsz.workflow.sla-default-hours} 等）
 *   <li>前端「公开配置」接口返回值（开关、限流阈值、特性开关等）
 * </ul>
 *
 * <p><b>缓存策略：</b>读取通过 {@code ydsz:config:{group}:{key}} 缓存至 Redis； 写入时 {@code @CacheEvict} 主动失效；TTL
 * 默认 30min。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.system.domain.entity.Config 系统配置实体
 */
@Data
@SuperBuilder
@NoArgsConstructor
@Schema(description = "系统配置视图对象")
public class ConfigVO {

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
