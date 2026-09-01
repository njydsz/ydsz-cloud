package com.njydsz.nextwiki.domain.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 分享访问日志 DTO
 *
 * <p>用于分享访问日志的创建操作，作为 Repository 接口 CUD 方法的入参。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@SuperBuilder
@NoArgsConstructor
@Schema(description = "分享访问日志数据传输对象")
public class ShareAccessLogDTO implements Serializable {

  private static final long serialVersionUID = 1L;

  @Schema(description = "主键ID")
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

  @Schema(description = "创建人")
  private String createdBy;

  @Schema(description = "租户ID")
  private String tenantId;
}
