package com.njydsz.nextwiki.domain.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 文件版本 DTO
 *
 * <p>用于文件版本的创建和更新操作，作为 Repository 接口 CUD 方法的入参。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@Schema(description = "文件版本数据传输对象")
public class FileVersionDTO implements Serializable {

  private static final long serialVersionUID = 1L;

  @Schema(description = "主键ID（更新时必填）")
  private String id;

  @Schema(description = "关联的文件节点ID")
  private String fileNodeId;

  @Schema(description = "版本号（从 1 开始递增）")
  private Integer versionNumber;

  @Schema(description = "该版本的存储对象键")
  private String storageKey;

  @Schema(description = "该版本的文件大小（字节）")
  private Long size;

  @Schema(description = "该版本的文件 SHA-256 哈希")
  private String fileHash;

  @Schema(description = "该版本的 MIME 类型")
  private String mimeType;

  @Schema(description = "版本说明")
  private String remark;

  @Schema(description = "变更类型：create / update / rollback")
  private String changeType;

  @Schema(description = "是否为当前活跃版本")
  private Boolean active;

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
