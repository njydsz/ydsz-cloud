package com.njydsz.nextwiki.domain.vo;

import java.io.Serializable;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 存储配额 VO
 *
 * @author ydsz-team
 * @since 26.09.01
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
  private LocalDateTime createdAt;

  @Schema(description = "更新人")
  private String updatedBy;

  @Schema(description = "更新时间")
  private LocalDateTime updatedAt;

  @Schema(description = "乐观锁版本号")
  private Integer revision;

  @Schema(description = "删除标记（0=正常，1=已删除）")
  private Integer deleted;
}
