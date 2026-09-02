package com.njydsz.nextwiki.domain.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 空间模板 DTO
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@SuperBuilder
@NoArgsConstructor
@Schema(description = "空间模板数据传输对象")
public class SpaceTemplateDTO implements Serializable {

  private static final long serialVersionUID = 1L;

  @Schema(description = "主键ID")
  private String id;

  @Schema(description = "模板名称")
  private String name;

  @Schema(description = "模板描述")
  private String description;

  @Schema(description = "模板分类：general / project / meeting / knowledge")
  private String category;

  @Schema(description = "模板图标 URL")
  private String iconUrl;

  @Schema(description = "租户ID（系统模板为 null）")
  private String tenantId;

  @Schema(description = "是否为系统内置模板")
  private Boolean systemFlag;

  @Schema(description = "是否公开")
  private Boolean publicAccess;

  @Schema(description = "模板结构 JSON")
  private String structureJson;

  @Schema(description = "排序序号")
  private Integer sortOrder;

  @Schema(description = "使用次数")
  private Integer usageCount;

  @Schema(description = "创建时间")
  private LocalDateTime createdAt;

  @Schema(description = "创建人")
  private String createdBy;

  @Schema(description = "更新时间")
  private LocalDateTime updatedAt;

  @Schema(description = "更新人")
  private String updatedBy;
}
