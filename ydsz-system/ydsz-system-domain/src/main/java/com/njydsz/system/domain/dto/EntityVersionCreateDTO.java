package com.njydsz.system.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 实体版本创建 DTO
 *
 * <p>聚合 {@code EntityVersionService#createVersion} 的 6 个入参，满足《云顶编码规范》参数数量
 * ≤ 5 的要求，同时让版本创建语义更内聚。
 *
 * @author ydsz-team
 * @since 1.1.0
 */
@Data
@Builder
@Schema(description = "实体版本创建参数")
public class EntityVersionCreateDTO {

  /** 资源类型（CONFIG/DICT/VARIABLE） */
  @Schema(description = "资源类型（CONFIG/DICT/VARIABLE）")
  private String resourceType;

  /** 资源唯一标识（configKey / typeCode / variableKey） */
  @Schema(description = "资源唯一标识")
  private String resourceKey;

  /** 资源分组（仅 CONFIG 类型使用） */
  @Schema(description = "资源分组（仅 CONFIG 类型使用）")
  private String resourceGroup;

  /** 版本号 */
  @Schema(description = "版本号")
  private String version;

  /** 变更说明 */
  @Schema(description = "变更说明")
  private String changeLog;

  /** 变更前 JSON 快照（可为 null） */
  @Schema(description = "变更前 JSON 快照")
  private String snapshotJson;
}
