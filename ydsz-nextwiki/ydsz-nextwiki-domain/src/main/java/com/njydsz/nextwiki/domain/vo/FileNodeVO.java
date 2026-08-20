package com.njydsz.nextwiki.domain.vo;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.nextwiki.domain.enums.NextwikiExceptionCode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 文件节点聚合根 VO
 *
 * <p><b>S2-P1-2 改进：引入领域行为方法</b>
 *
 * <p>文件节点是知识库系统的核心聚合根，包含完整的业务字段和领域行为：
 *
 * <ul>
 *   <li><b>基础属性：</b>节点 ID、父节点 ID、名称、类型、路径、层级
 *   <li><b>存储属性：</b>存储键、存储桶、存储类型、文件哈希、大小
 *   <li><b>状态属性：</b>版本号、排序号、星标、分享状态、锁定状态
 *   <li><b>关系属性：</b>子节点列表、标签列表
 * </ul>
 *
 * <p><b>领域行为：</b>
 *
 * <ul>
 *   <li>{@link #isFolder()} / {@link #isFile()} — 类型判断
 *   <li>{@link #isRoot()} — 根节点判断（父节点为 null 或 "0"）
 *   <li>{@link #isDescendantOf(FileNodeVO)} — 后代关系判断（基于路径前缀）
 *   <li>{@link #canMoveTo(FileNodeVO)} — 移动合法性校验（防循环）
 *   <li>{@link #calculatePath(String, String)} — 静态路径计算工具
 * </ul>
 *
 * <p><b>设计说明：</b>
 *
 * <ul>
 *   <li>本 VO 兼具领域聚合根与展示视图职责，适用于当前单体架构
 *   <li>未来微服务拆分时，可将展示字段抽取为 {@code FileNodeView}
 *   <li>数据持久化通过 {@code FileNodeDTO} → {@code FileNodeDO} 转换实现
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Builder
@Schema(description = "文件节点聚合根")
public class FileNodeVO implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 节点类型：文件夹 */
  public static final String TYPE_FOLDER = "folder";

  /** 节点类型：文件 */
  public static final String TYPE_FILE = "file";

  /** 根节点父 ID 约定值 */
  public static final String ROOT_PARENT_ID = "0";

  /** 节点状态：活跃 */
  public static final String STATUS_ACTIVE = "active";

  /** 节点状态：已锁定（Check-out 防并发编辑） */
  public static final String STATUS_LOCKED = "locked";

  /** 节点状态：已归档（冷数据） */
  public static final String STATUS_ARCHIVED = "archived";

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

  @Schema(description = "存储类型（如 STANDARD / GLACIER / DEEP_ARCHIVE）")
  private String storageClass;

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

  @Schema(description = "节点状态: active / locked / archived")
  private String status;

  @Schema(description = "共享状态: private / shared / public")
  private String shareStatus;

  @Schema(description = "租户ID")
  private String tenantId;

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

  // ==================== 领域行为方法 ====================

  /**
   * 判断是否为目录。
   *
   * @return {@code true} 表示目录
   */
  public boolean isFolder() {
    return TYPE_FOLDER.equals(nodeType);
  }

  /**
   * 判断是否为文件。
   *
   * @return {@code true} 表示文件
   */
  public boolean isFile() {
    return TYPE_FILE.equals(nodeType);
  }

  /**
   * 判断是否为根节点。
   *
   * <p>根节点特征：{@code parentId} 为 {@code null}、空字符串或 {@link #ROOT_PARENT_ID}（"0"）。
   *
   * @return {@code true} 表示根节点
   */
  public boolean isRoot() {
    return parentId == null || parentId.isEmpty() || ROOT_PARENT_ID.equals(parentId);
  }

  /**
   * 判断是否为活跃状态（未锁定/未归档）。
   *
   * @return {@code true} 表示可正常编辑
   */
  public boolean isActive() {
    return STATUS_ACTIVE.equals(status) || status == null;
  }

  /**
   * 判断是否处于锁定状态。
   *
   * @return {@code true} 表示已被 Check-out 锁定
   */
  public boolean isLocked() {
    return STATUS_LOCKED.equals(status);
  }

  /**
   * 判断当前节点是否为指定祖先节点的后代（基于路径前缀匹配）。
   *
   * <p>若任一节点路径为 {@code null}，返回 {@code false}。
   *
   * @param ancestor 祖先节点
   * @return {@code true} 表示当前节点是 {@code ancestor} 的后代或自身
   */
  public boolean isDescendantOf(FileNodeVO ancestor) {
    if (ancestor == null || ancestor.getPath() == null || this.path == null) {
      return false;
    }
    if (this.id != null && this.id.equals(ancestor.getId())) {
      return true;
    }
    return this.path.startsWith(ancestor.getPath());
  }

  /**
   * 校验当前节点是否可以移动到目标父目录。
   *
   * <p><b>校验规则：</b>
   *
   * <ul>
   *   <li>目标父目录不能为 {@code null}
   *   <li>目标父目录必须是目录类型
   *   <li>不能将目录移动到自身或其子树下（防循环）
   * </ul>
   *
   * @param targetParent 目标父目录节点
   * @throws BusinessException 移动不合法时抛出
   */
  public void canMoveTo(FileNodeVO targetParent) {
    if (targetParent == null) {
      throw new BusinessException(NextwikiExceptionCode.FILE_FOLDER_NOT_FOUND);
    }
    if (!targetParent.isFolder()) {
      throw new BusinessException(NextwikiExceptionCode.FILE_PARENT_NOT_FOLDER);
    }
    if (this.isFolder() && this.isDescendantOf(targetParent)) {
      throw new BusinessException(NextwikiExceptionCode.FILE_MOVE_TO_SELF);
    }
  }

  /**
   * 构建子节点路径（静态工具方法）。
   *
   * <p>路径格式：{@code /父路径/子名称/}（末尾始终以 "/" 结尾）。
   *
   * @param parentPath 父目录路径（可为 {@code null} 或空）
   * @param childName 子节点名称
   * @return 子节点路径
   */
  public static String calculatePath(String parentPath, String childName) {
    StringBuilder sb = new StringBuilder();
    if (parentPath == null || parentPath.isEmpty()) {
      sb.append('/');
    } else {
      if (!parentPath.startsWith("/")) {
        sb.append('/');
      }
      sb.append(parentPath);
      if (!parentPath.endsWith("/")) {
        sb.append('/');
      }
    }
    sb.append(childName);
    sb.append('/');
    return sb.toString();
  }

  // ==================== 状态变更方法（封装 setter，表达领域意图） ====================

  /**
   * 变更父节点（移动操作）。
   *
   * <p>封装 parentId、path、level、sort 的更新，表达"移动到目标父节点下"的领域意图。
   *
   * @param newParentId 新父节点 ID
   * @param newPath 新路径
   * @param newLevel 新层级
   * @param newSort 新排序号
   * @param operatorId 操作人 ID
   */
  public void changeParent(String newParentId, String newPath, int newLevel, int newSort, String operatorId) {
    this.parentId = newParentId;
    this.path = newPath;
    this.level = newLevel;
    this.sort = newSort;
    this.updatedBy = operatorId;
  }

  /**
   * 变更名称（重命名操作）。
   *
   * <p>封装 name、path 的更新，表达"重命名"的领域意图。
   *
   * @param newName 新名称
   * @param newPath 新路径
   * @param operatorId 操作人 ID
   */
  public void changeName(String newName, String newPath, String operatorId) {
    this.name = newName;
    this.path = newPath;
    this.updatedBy = operatorId;
  }

  /**
   * 更新操作人（通用）。
   *
   * @param operatorId 操作人 ID
   */
  public void markUpdatedBy(String operatorId) {
    this.updatedBy = operatorId;
  }
}
