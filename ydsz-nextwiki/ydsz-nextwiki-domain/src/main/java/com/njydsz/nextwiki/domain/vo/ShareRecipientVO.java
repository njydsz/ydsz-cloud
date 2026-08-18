package com.njydsz.nextwiki.domain.vo;

import java.io.Serializable;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 分享目标用户 VO
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Builder
@Schema(description = "分享目标用户信息")
public class ShareRecipientVO implements Serializable {

  private static final long serialVersionUID = 1L;

  @Schema(description = "接收记录ID")
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

  @Schema(description = "创建时间")
  private LocalDateTime createdAt;
}
