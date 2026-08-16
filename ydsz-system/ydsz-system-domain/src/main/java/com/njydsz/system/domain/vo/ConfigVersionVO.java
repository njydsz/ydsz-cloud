package com.njydsz.system.domain.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 系统配置版本 VO
 *
 * <p>对应 {@code ydsz_config_version} 表的展示视图，是「配置版本管理」列表 / 详情接口的返回值类型。
 * 配置版本是对<b>单个配置键</b>的快照管理，每次配置项变更产生一个新版本。
 *
 * <p><b>字段语义：</b>
 *
 * <ul>
 *   <li>{@code resourceKey} — 配置键，关联 {@link ConfigVO#configKey}
 *   <li>{@code configGroup} — 配置分组（冗余展示）
 *   <li>{@code version} — 版本号（时间戳格式，如 {@code v1734567890123}）
 *   <li>{@code changeLog} — 变更说明
 *   <li>{@code effectiveDate} — 生效时间
 *   <li>{@code snapshotJson} — 快照数据（JSON 格式），包含该版本下配置项的完整数据
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.system.domain.entity.ConfigVersion 配置版本实体
 * @see ConfigVO 配置项 VO
 */
@Data
@Schema(description = "系统配置版本视图对象")
public class ConfigVersionVO {

  @Schema(description = "主键 ID")
  private String id;

  @Schema(description = "配置键")
  private String resourceKey;

  @Schema(description = "配置分组")
  private String configGroup;

  @Schema(description = "版本号")
  private String version;

  @Schema(description = "变更说明")
  private String changeLog;

  @Schema(description = "生效时间")
  private LocalDateTime effectiveDate;

  @Schema(description = "快照数据（JSON）")
  private String snapshotJson;

  @Schema(description = "创建时间")
  private LocalDateTime createdAt;
}
