package com.njydsz.nextwiki.domain.dto;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 文件节点 DTO
 *
 * <p>用于文件节点的创建和更新操作，作为 Repository 接口 CUD 方法的入参。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@Schema(description = "文件节点数据传输对象")
public class FileNodeDTO implements Serializable {

  private static final long serialVersionUID = 1L;

  @Schema(description = "主键ID（更新时必填）")
  private String id;

  @Schema(description = "父节点ID")
  private String parentId;

  @Schema(description = "节点名称")
  private String name;

  @Schema(description = "节点类型：folder / file")
  private String nodeType;

  @Schema(description = "文件扩展名")
  private String suffix;

  @Schema(description = "文件大小（字节）")
  private Long size;

  @Schema(description = "底层存储对象键")
  private String storageKey;

  @Schema(description = "存储桶名称")
  private String bucketName;

  @Schema(description = "MIME 类型")
  private String mimeType;

  @Schema(description = "目录路径")
  private String path;

  @Schema(description = "层级深度")
  private Integer level;

  @Schema(description = "排序序号")
  private Integer sort;

  @Schema(description = "当前版本号")
  private Integer currentVersion;

  @Schema(description = "文件 SHA-256 哈希")
  private String fileHash;

  @Schema(description = "缩略图存储键")
  private String thumbnailKey;

  @Schema(description = "是否已生成预览")
  private Boolean previewReady;

  @Schema(description = "是否星标文件")
  private Boolean starred;

  @Schema(description = "共享状态：private / shared / public")
  private String shareStatus;

  @Schema(description = "存储类型：STANDARD / GLACIER / DEEP_ARCHIVE")
  private String storageClass;

  @Schema(description = "创建人（操作人）")
  private String createdBy;

  @Schema(description = "更新人（操作人）")
  private String updatedBy;

  @Schema(description = "租户ID")
  private String tenantId;
}
