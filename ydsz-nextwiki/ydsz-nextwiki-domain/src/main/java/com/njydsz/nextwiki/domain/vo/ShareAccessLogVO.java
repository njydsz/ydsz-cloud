package com.njydsz.nextwiki.domain.vo;

import java.io.Serializable;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 分享访问日志 VO
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Builder
@Schema(description = "分享访问日志信息")
public class ShareAccessLogVO implements Serializable {

  private static final long serialVersionUID = 1L;

  @Schema(description = "日志记录ID")
  private String id;

  @Schema(description = "分享链接ID")
  private String shareId;

  @Schema(description = "分享码")
  private String shareCode;

  @Schema(description = "文件节点ID")
  private String fileNodeId;

  @Schema(description = "访问者用户ID")
  private String visitorId;

  @Schema(description = "访问者名称")
  private String visitorName;

  @Schema(description = "访问者IP地址")
  private String visitorIp;

  @Schema(description = "访问者User-Agent")
  private String userAgent;

  @Schema(description = "访问类型：VIEW/DOWNLOAD/EDIT")
  private String accessType;

  @Schema(description = "访问状态：SUCCESS/FAIL")
  private String accessStatus;

  @Schema(description = "失败原因")
  private String failReason;

  @Schema(description = "访问时间")
  private LocalDateTime accessTime;

  @Schema(description = "创建时间")
  private LocalDateTime createdAt;
}
