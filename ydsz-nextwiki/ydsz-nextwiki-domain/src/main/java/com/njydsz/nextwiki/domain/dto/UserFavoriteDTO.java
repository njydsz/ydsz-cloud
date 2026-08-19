package com.njydsz.nextwiki.domain.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 用户收藏夹 DTO
 *
 * <p>用于收藏夹数据的传输，作为 Repository 接口 CUD 方法的入参。
 *
 * @author ydsz-team
 * @since 1.1.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@Schema(description = "用户收藏夹数据传输对象")
public class UserFavoriteDTO implements Serializable {

  private static final long serialVersionUID = 1L;

  @Schema(description = "主键ID")
  private String id;

  @Schema(description = "用户ID")
  private String userId;

  @Schema(description = "节点ID")
  private String nodeId;

  @Schema(description = "租户ID")
  private String tenantId;

  @Schema(description = "排序序号")
  private Integer sortOrder;

  @Schema(description = "创建时间")
  private LocalDateTime createdAt;

  @Schema(description = "创建人")
  private String createdBy;

  @Schema(description = "更新时间")
  private LocalDateTime updatedAt;

  @Schema(description = "更新人")
  private String updatedBy;
}
