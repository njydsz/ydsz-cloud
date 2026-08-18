package com.njydsz.nextwiki.domain.dto;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 存储配额 DTO
 *
 * <p>用于存储配额的创建和更新操作，作为 Repository 接口 CUD 方法的入参。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@Schema(description = "存储配额数据传输对象")
public class StorageQuotaDTO implements Serializable {

  private static final long serialVersionUID = 1L;

  @Schema(description = "主键ID（更新时必填）")
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
}
