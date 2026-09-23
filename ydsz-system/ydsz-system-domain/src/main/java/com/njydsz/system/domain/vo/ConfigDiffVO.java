package com.njydsz.system.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 配置版本对比 VO（Nacos / Apollo 标配的「配置 diff」功能）。
 *
 * <p>展示两个版本之间按字段粒度的差异，前端可按此高亮变更行。
 *
 * @author ydsz-team
 * @since 26.09.08
 */
@Data
@Schema(description = "配置版本对比结果")
public class ConfigDiffVO {

  /** 源版本（基准） */
  @Schema(description = "源版本信息")
  private VersionBrief fromVersion;

  /** 目标版本（对比对象） */
  @Schema(description = "目标版本信息")
  private VersionBrief toVersion;

  /** 字段级差异列表 */
  @Schema(description = "字段级差异列表")
  private List<FieldDiff> diffs;

  /** 版本摘要信息。 */
  @Data
  @Schema(description = "版本摘要")
  public static class VersionBrief {
    /** 版本号 */
    @Schema(description = "版本号")
    private String version;
    /** 快照 JSON（完整内容） */
    @Schema(description = "快照 JSON（完整内容）")
    private String snapshotJson;
    /** 生效时间 */
    @Schema(description = "生效时间")
    private String effectiveDate;
  }

  /** 单个字段的差异。 */
  @Data
  @Schema(description = "字段差异")
  public static class FieldDiff {
    /** 字段路径（如 configValue / description / status） */
    @Schema(description = "字段路径（如 configValue / description / status）")
    private String field;
    /** 变更类型：MODIFIED / ADDED / REMOVED */
    @Schema(description = "变更类型：MODIFIED / ADDED / REMOVED")
    private ChangeType changeType;
    /** 旧值（源版本） */
    @Schema(description = "旧值（源版本）")
    private String oldValue;
    /** 新值（目标版本） */
    @Schema(description = "新值（目标版本）")
    private String newValue;
  }

  /** 变更类型枚举。 */
  public enum ChangeType {
    /** 修改（旧值 → 新值） */
    MODIFIED,
    /** 新增（旧值不存在） */
    ADDED,
    /** 删除（新值不存在） */
    REMOVED
  }
}
