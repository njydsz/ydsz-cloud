package com.njydsz.nextwiki.domain.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 存储配额 VO
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Builder
@Schema(description = "存储配额信息")
public class StorageQuotaVO implements Serializable {

  private static final long serialVersionUID = 1L;

  @Schema(description = "配额记录ID")
  private String id;

  @Schema(description = "配额维度：user / tenant / project")
  private String scopeType;

  @Schema(description = "维度ID")
  private String scopeId;

  @Schema(description = "配额上限（字节）")
  private Long quotaLimit;

  @Schema(description = "已使用量（字节）")
  private Long quotaUsed;

  @Schema(description = "文件数量上限")
  private Integer fileCountLimit;

  @Schema(description = "已使用文件数量")
  private Integer fileCountUsed;

  @Schema(description = "创建人")
  private String createdBy;

  @Schema(description = "创建时间")
  private java.time.LocalDateTime createdAt;
}
