package com.njydsz.nextwiki.domain.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 分享目标用户 DTO
 *
 * <p>用于分享目标用户的创建操作，作为 Repository 接口 CUD 方法的入参。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@SuperBuilder
@NoArgsConstructor
@Schema(description = "分享目标用户数据传输对象")
public class ShareRecipientDTO implements Serializable {

  private static final long serialVersionUID = 1L;

  @Schema(description = "主键ID")
  private String id;

  @Schema(description = "分享链接ID")
  private String shareId;

  @Schema(description = "接收者类型：USER/DEPT/ROLE")
  private String recipientType;

  @Schema(description = "接收者ID")
  private String recipientId;

  @Schema(description = "接收者名称")
  private String recipientName;

  @Schema(description = "状态：ACTIVE/VIEWED/REVOKED")
  private String status;

  @Schema(description = "首次查看时间")
  private LocalDateTime viewedAt;

  @Schema(description = "创建人")
  private String createdBy;

  @Schema(description = "更新人")
  private String updatedBy;

  @Schema(description = "创建时间")
  private LocalDateTime createdAt;

  @Schema(description = "更新时间")
  private LocalDateTime updatedAt;

  @Schema(description = "租户ID")
  private String tenantId;
}
