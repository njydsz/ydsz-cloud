package com.njydsz.nextwiki.domain.vo;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 文件节点树形 VO
 *
 * <p>领域层返回的文件节点视图对象，包含完整的业务字段。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Builder
@Schema(description = "文件节点树形结构")
public class FileNodeVO implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 节点类型：文件夹 */
  public static final String TYPE_FOLDER = "folder";

  /** 节点类型：文件 */
  public static final String TYPE_FILE = "file";

  @Schema(description = "节点ID")
  private String id;

  @Schema(description = "父节点ID")
  private String parentId;

  @Schema(description = "节点名称")
  private String name;

  @Schema(description = "节点类型: folder / file")
  private String nodeType;

  @Schema(description = "文件扩展名")
  private String suffix;

  @Schema(description = "文件大小（字节）")
  private Long size;

  @Schema(description = "MIME 类型")
  private String mimeType;

  @Schema(description = "底层存储对象键")
  private String storageKey;

  @Schema(description = "存储桶名称")
  private String bucketName;

  @Schema(description = "文件 SHA-256 哈希")
  private String fileHash;

  @Schema(description = "目录路径")
  private String path;

  @Schema(description = "层级深度")
  private Integer level;

  @Schema(description = "排序序号")
  private Integer sort;

  @Schema(description = "当前版本号")
  private Integer currentVersion;

  @Schema(description = "缩略图存储键")
  private String thumbnailKey;

  @Schema(description = "是否已生成预览")
  private Boolean previewReady;

  @Schema(description = "是否星标")
  private Boolean starred;

  @Schema(description = "共享状态")
  private String shareStatus;

  @Schema(description = "创建人")
  private String createdBy;

  @Schema(description = "更新人")
  private String updatedBy;

  @Schema(description = "创建时间")
  private LocalDateTime createdAt;

  @Schema(description = "更新时间")
  private LocalDateTime updatedAt;

  @Schema(description = "子节点列表")
  private List<FileNodeVO> children;

  @Schema(description = "标签列表")
  private List<String> tags;

  /** 是否为目录 */
  public boolean isFolder() {
    return TYPE_FOLDER.equals(nodeType);
  }

  /** 是否为文件 */
  public boolean isFile() {
    return TYPE_FILE.equals(nodeType);
  }
}
