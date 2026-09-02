package com.njydsz.nextwiki.domain.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 空间成员 DTO
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@SuperBuilder
@NoArgsConstructor
@Schema(description = "空间成员数据传输对象")
public class SpaceMemberDTO implements Serializable {

  private static final long serialVersionUID = 1L;

  @Schema(description = "主键ID")
  private String id;

  @Schema(description = "空间ID")
  private String spaceId;

  @Schema(description = "用户ID")
  private String userId;

  @Schema(description = "角色：owner / admin / editor / viewer")
  private String role;

  @Schema(description = "租户ID")
  private String tenantId;

  @Schema(description = "加入时间")
  private LocalDateTime joinedAt;

  @Schema(description = "创建人")
  private String createdBy;

  @Schema(description = "更新时间")
  private LocalDateTime updatedAt;

  @Schema(description = "更新人")
  private String updatedBy;
}
