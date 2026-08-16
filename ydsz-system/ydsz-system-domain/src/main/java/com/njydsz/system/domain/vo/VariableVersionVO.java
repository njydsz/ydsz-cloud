package com.njydsz.system.domain.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 系统变量版本 VO
 *
 * <p>对应 {@code ydsz_variable_version} 表的展示视图，是「变量版本管理」列表 / 详情接口的返回值类型。
 * 变量版本是对<b>单个变量键</b>的快照管理，每次变量变更产生一个新版本。
 *
 * <p><b>字段语义：</b>
 *
 * <ul>
 *   <li>{@code resourceKey} — 变量键，关联 {@link VariableVO#variableKey}
 *   <li>{@code version} — 版本号（时间戳格式，如 {@code v1734567890123}）
 *   <li>{@code changeLog} — 变更说明
 *   <li>{@code effectiveDate} — 生效时间
 *   <li>{@code snapshotJson} — 快照数据（JSON 格式），包含该版本下变量的完整数据
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.system.domain.entity.VariableVersion 变量版本实体
 * @see VariableVO 变量 VO
 */
@Data
@Schema(description = "系统变量版本视图对象")
public class VariableVersionVO {

  @Schema(description = "主键 ID")
  private String id;

  @Schema(description = "变量键")
  private String resourceKey;

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
