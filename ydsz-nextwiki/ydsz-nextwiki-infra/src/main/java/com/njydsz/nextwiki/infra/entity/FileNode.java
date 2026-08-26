package com.njydsz.nextwiki.infra.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 网盘文件节点实体
 *
 * <p>统一表示文件和目录（通过 {@link #nodeType} 区分），构成目录树的核心节点。 与底层存储层（{@code IFileStorage}）的对象一一对应，但额外持久化了
 * 目录结构、版本号、标签、分享状态等业务元数据。
 *
 * <p><b>核心设计：</b>
 *
 * <ul>
 *   <li>使用 {@code parentId} + {@code path} 实现目录树（闭包路径模式）
 *   <li>{@code storageKey} 对应底层存储的对象键（objectName）
 *   <li>{@code version} 记录当前版本号，支持版本回溯
 *   <li>{@code deletedTime} 支持回收站功能（逻辑删除 + 过期清理）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@TableName("ydsz_wiki_file_node")
public class FileNode extends MpBaseEntity<String> implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 节点类型：文件夹 */
  public static final String TYPE_FOLDER = "folder";

  /** 节点类型：文件 */
  public static final String TYPE_FILE = "file";

  /** 父节点ID（根目录为 "0"） */
  private String parentId;

  /** 节点名称（文件名或目录名） */
  private String name;

  /** 节点类型：folder / file */
  private String nodeType;

  /** 文件扩展名（小写，不含点；文件夹为空） */
  private String suffix;

  /** 文件大小（字节；文件夹为 0） */
  private Long size;

  /** 底层存储对象键（objectName） */
  private String storageKey;

  /** 存储桶名称 */
  private String bucketName;

  /** MIME 类型 */
  private String mimeType;

  /** 目录路径（如 /root/docs/contract/）用于快速判断层级关系 */
  private String path;

  /** 层级深度（根为 0） */
  private Integer level;

  /** 排序序号 */
  private Integer sort;

  /** 当前版本号（从 1 开始，每次更新 +1） */
  private Integer currentVersion;

  /** 文件 SHA-256 哈希（用于秒传去重） */
  private String fileHash;

  /** 缩略图存储键 */
  private String thumbnailKey;

  /** 是否已生成预览 */
  @TableField("preview_ready")
  private Boolean previewReady;

  /** 是否星标文件 */
  @TableField("starred")
  private Boolean starred;

  /** 共享状态：private / shared / public */
  private String shareStatus;

  /** 逻辑删除时间（回收站功能：删除时记录时间，30 天后永久删除） */
  @TableField("deleted_time")
  private LocalDateTime deletedTime;

  /** 原始路径（删除前的完整路径，用于恢复） */
  @TableField("original_path")
  private String originalPath;

  /** 存储类型：STANDARD / GLACIER / DEEP_ARCHIVE（冷数据归档） */
  @TableField("storage_class")
  private String storageClass;

  /** 是否为目录 */
  public boolean isFolder() {
    return TYPE_FOLDER.equals(nodeType);
  }

  /** 是否为文件 */
  public boolean isFile() {
    return TYPE_FILE.equals(nodeType);
  }
}
