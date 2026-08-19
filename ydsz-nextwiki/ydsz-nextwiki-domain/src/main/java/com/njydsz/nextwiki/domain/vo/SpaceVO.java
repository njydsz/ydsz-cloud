package com.njydsz.nextwiki.domain.vo;

import java.io.Serializable;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 知识库空间视图对象
 *
 * <p>返回给前端的空间信息。
 *
 * @author ydsz-team
 * @since 1.2.0
 */
@Data
@Builder
@Schema(description = "知识库空间视图对象")
public class SpaceVO implements Serializable {

  private static final long serialVersionUID = 1L;

  @Schema(description = "空间ID")
  private String id;

  @Schema(description = "空间名称")
  private String name;

  @Schema(description = "空间描述")
  private String description;

  @Schema(description = "空间图标 URL")
  private String iconUrl;

  @Schema(description = "空间封面 URL")
  private String coverUrl;

  @Schema(description = "所有者ID")
  private String ownerId;

  @Schema(description = "状态：active / archived / deleted")
  private String status;

  @Schema(description = "可见性：private / organization / public")
  private String visibility;

  @Schema(description = "排序序号")
  private Integer sortOrder;

  @Schema(description = "成员数量")
  private Integer memberCount;

  @Schema(description = "节点数量")
  private Integer nodeCount;

  @Schema(description = "空间独立配额（字节）")
  private Long quotaLimit;

  @Schema(description = "已使用配额（字节）")
  private Long quotaUsed;

  @Schema(description = "创建时间")
  private LocalDateTime createdAt;

  @Schema(description = "更新时间")
  private LocalDateTime updatedAt;
}
