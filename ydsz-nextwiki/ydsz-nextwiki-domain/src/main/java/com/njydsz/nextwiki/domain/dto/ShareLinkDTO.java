package com.njydsz.nextwiki.domain.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 分享链接 DTO
 *
 * <p>用于分享链接的创建和更新操作，作为 Repository 接口 CUD 方法的入参。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@Schema(description = "分享链接数据传输对象")
public class ShareLinkDTO implements Serializable {

  private static final long serialVersionUID = 1L;

  @Schema(description = "主键ID（更新时必填）")
  private String id;

  @Schema(description = "关联的文件节点ID")
  private String fileNodeId;

  @Schema(description = "分享码（URL 唯一标识）")
  private String shareCode;

  @Schema(description = "提取码")
  private String extractCode;

  @Schema(description = "分享类型：view / download / edit")
  private String shareType;

  @Schema(description = "过期时间")
  private LocalDateTime expireTime;

  @Schema(description = "最大访问次数")
  private Integer maxAccessCount;

  @Schema(description = "已访问次数")
  private Integer accessCount;

  @Schema(description = "分享状态：active / expired / revoked")
  private String status;

  @Schema(description = "分享密码（BCrypt 加密）")
  private String password;

  @Schema(description = "分享目标类型：PUBLIC / USER / DEPT")
  private String shareTargetType;

  @Schema(description = "分享标题")
  private String title;

  @Schema(description = "是否已发送到期提醒")
  private Boolean reminderSent;

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
