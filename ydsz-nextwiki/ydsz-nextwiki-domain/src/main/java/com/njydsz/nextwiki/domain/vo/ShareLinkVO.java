package com.njydsz.nextwiki.domain.vo;

import java.io.Serializable;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 分享链接 VO
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@Builder
@Schema(description = "分享链接信息")
public class ShareLinkVO implements Serializable {

  private static final long serialVersionUID = 1L;

  @Schema(description = "分享ID")
  private String id;

  @Schema(description = "关联的文件节点ID")
  private String fileNodeId;

  @Schema(description = "分享码")
  private String shareCode;

  @Schema(description = "提取码")
  private String extractCode;

  @Schema(description = "文件名")
  private String fileName;

  @Schema(description = "分享类型")
  private String shareType;

  @Schema(description = "过期时间")
  private LocalDateTime expireTime;

  @Schema(description = "最大访问次数")
  private Integer maxAccessCount;

  @Schema(description = "已访问次数")
  private Integer accessCount;

  @Schema(description = "分享状态")
  private String status;

  @Schema(description = "分享目标类型：PUBLIC/USER/DEPT")
  private String shareTargetType;

  @Schema(description = "分享密码（敏感字段，序列化时建议脱敏）")
  private String password;

  @Schema(description = "到期提醒是否已发送")
  private Boolean reminderSent;

  @Schema(description = "分享标题（可选）")
  private String title;

  @Schema(description = "是否需要密码")
  private Boolean hasPassword;

  @Schema(description = "分享URL")
  private String shareUrl;

  @Schema(description = "创建人")
  private String createdBy;

  @Schema(description = "更新人")
  private String updatedBy;

  @Schema(description = "创建时间")
  private LocalDateTime createdAt;

  @Schema(description = "更新时间")
  private LocalDateTime updatedAt;
}
