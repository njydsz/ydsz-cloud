package com.njydsz.system.domain.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 统一实体版本 VO
 *
 * <p>对应 {@code ydsz_entity_version} 表的展示视图，是「版本管理」列表 / 详情接口的返回值类型。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.system.domain.entity.EntityVersion 实体版本实体
 */
@Data
@Schema(description = "实体版本视图对象")
public class EntityVersionVO {

  @Schema(description = "主键 ID")
  private String id;

  @Schema(description = "资源类型: CONFIG/DICT/VARIABLE")
  private String resourceType;

  @Schema(description = "资源唯一标识")
  private String resourceKey;

  @Schema(description = "资源分组")
  private String resourceGroup;

  @Schema(description = "版本号")
  private String version;

  @Schema(description = "变更说明")
  private String changeLog;

  @Schema(description = "生效时间")
  private LocalDateTime effectiveDate;
}
