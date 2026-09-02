package com.njydsz.nextwiki.domain.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 用户最近访问 DTO
 *
 * <p>用于最近访问数据的传输，作为 Repository 接口 CUD 方法的入参。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@SuperBuilder
@NoArgsConstructor
@Schema(description = "用户最近访问数据传输对象")
public class UserRecentDTO implements Serializable {

  private static final long serialVersionUID = 1L;

  @Schema(description = "主键ID")
  private String id;

  @Schema(description = "用户ID")
  private String userId;

  @Schema(description = "节点ID")
  private String nodeId;

  @Schema(description = "租户ID")
  private String tenantId;

  @Schema(description = "访问类型: view / edit / download")
  private String accessType;

  @Schema(description = "最近访问时间")
  private LocalDateTime accessedAt;

  @Schema(description = "创建时间")
  private LocalDateTime createdAt;

  @Schema(description = "更新时间")
  private LocalDateTime updatedAt;
}
