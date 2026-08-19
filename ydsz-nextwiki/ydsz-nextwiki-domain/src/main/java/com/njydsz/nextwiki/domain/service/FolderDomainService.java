package com.njydsz.nextwiki.domain.service;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.nextwiki.domain.enums.NextwikiExceptionCode;
import com.njydsz.nextwiki.domain.vo.FileNodeVO;

/**
 * 目录树领域服务
 *
 * <p>封装目录树操作的核心业务逻辑：构建目录、移动、重命名、路径计算、统计。
 * 本服务为纯领域逻辑组件，不执行任何数据访问或事件发布；数据访问与副作用由 server 层负责。
 *
 * <p><b>路径规则：</b>
 *
 * <ul>
 *   <li>根目录路径为 "/"，ID 约定为 "0"
 *   <li>子目录路径为 "/父路径/目录名/"，如 "/docs/contract/"
 *   <li>路径末尾始终以 "/" 结尾，便于前缀查询
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class FolderDomainService {

  /** 分布式 ID 生成器 */
  private final SnowflakeIdGenerator snowflakeIdGenerator;

  /**
   * 构建目录节点（纯领域逻辑，不执行持久化）。
   *
   * <p>由 server 层传入父目录与同级子节点列表，本方法完成同名校验、路径计算与实体构建。 返回的 {@link FileNodeVO} 实例需由 server 层持久化。
   *
   * @param parent 父目录节点（已由 server 层解析）
   * @param siblings 父目录下全部子节点（用于同名校验与排序号计算）
   * @param name 新目录名称
   * @param userId 操作人 ID
   * @return 构建完成的 {@link FileNodeVO} 实例（未持久化）
   * @throws BusinessException 同名目录已存在时抛出
   */
  public FileNodeVO createFolder(FileNodeVO parent, List<FileNodeVO> siblings, String name, String userId) {
    // 检查同名目录
    boolean nameExists =
        siblings.stream().anyMatch(c -> c.getName().equalsIgnoreCase(name) && c.isFolder());
    if (nameExists) {
      throw BusinessException.of(NextwikiExceptionCode.FILE_ALREADY_EXISTS).data("name", name);
    }

    String path = buildPath(parent.getPath(), name);
    int level = parent.getLevel() + 1;

    FileNodeVO folder =
        FileNodeVO.builder()
            .id(String.valueOf(snowflakeIdGenerator.nextId()))
            .parentId(parent.getId())
            .name(name)
            .nodeType(FileNodeVO.TYPE_FOLDER)
            .size(0L)
            .path(path)
            .level(level)
            .sort(siblings.size())
            .currentVersion(0)
            .previewReady(false)
            .starred(false)
            .shareStatus("private")
            .createdBy(userId)
            .updatedBy(userId)
            .build();

    log.info("[FolderDomainService] 构建目录: name={}, path={}, userId={}", name, path, userId);
    return folder;
  }

  /**
   * 执行移动操作（纯领域逻辑，不执行持久化）。
   *
   * <p>将 {@code node} 移动到 {@code targetParent} 下，计算新路径、层级与排序号。 若节点为目录且目标为其自身或其子树，则抛出循环移动异常。
   *
   * @param node 待移动的节点（已由 server 层加载）
   * @param targetParent 目标父目录节点（已由 server 层加载）
   * @param targetSiblings 目标父目录下全部子节点（用于排序号计算）
   * @param userId 操作人 ID
   * @return 更新后的 {@link FileNodeVO} 实例（未持久化）
   * @throws BusinessException 目标为自身/子树，或目标不是目录时抛出
   */
  public FileNodeVO move(
      FileNodeVO node, FileNodeVO targetParent, List<FileNodeVO> targetSiblings, String userId) {
    if (targetParent == null || !targetParent.isFolder()) {
      throw new BusinessException(NextwikiExceptionCode.FILE_PARENT_NOT_FOLDER);
    }

    // 防止将目录移动到自身或其子目录下
    if (node.isFolder() && isDescendantOf(targetParent, node)) {
      throw new BusinessException(NextwikiExceptionCode.FILE_MOVE_TO_SELF);
    }

    String oldPath = node.getPath();
    String newPath = buildPath(targetParent.getPath(), node.getName());
    int newLevel = targetParent.getLevel() + 1;

    node.setParentId(targetParent.getId());
    node.setPath(newPath);
    node.setLevel(newLevel);
    node.setSort(targetSiblings.size());
    node.setUpdatedBy(userId);

    log.info(
        "[FolderDomainService] 移动: nodeId={}, oldPath={}, newPath={}", node.getId(), oldPath, newPath);
    return node;
  }

  /**
   * 执行重命名操作（纯领域逻辑，不执行持久化）。
   *
   * <p>更新节点名称与路径。若节点为目录，路径变更需由 server 层同步到子节点。
   *
   * @param node 待重命名的节点（已由 server 层加载）
   * @param parent 节点的父目录（用于路径计算；根节点可为 null）
   * @param newName 新名称
   * @param userId 操作人 ID
   * @return 更新后的 {@link FileNodeVO} 实例（未持久化）
   */
  public FileNodeVO rename(FileNodeVO node, FileNodeVO parent, String newName, String userId) {
    String oldName = node.getName();
    String newPath = parent != null ? buildPath(parent.getPath(), newName) : "/" + newName + "/";

    node.setName(newName);
    node.setPath(newPath);
    node.setUpdatedBy(userId);

    log.info(
        "[FolderDomainService] 重命名: nodeId={}, oldName={}, newName={}", node.getId(), oldName, newName);
    return node;
  }

  /**
   * 计算目录统计信息（纯领域逻辑）。
   *
   * @param folder 目录节点（已由 server 层加载）
   * @param descendants 目录的全部后代节点（不含目录自身；已由 server 层加载）
   * @return 统计结果
   */
  public FolderStats getStats(FileNodeVO folder, List<FileNodeVO> descendants) {
    if (folder == null || !folder.isFolder()) {
      return new FolderStats(0, 0, 0);
    }

    int fileCount = 0;
    int folderCount = 0;
    long totalSize = 0;

    for (FileNodeVO desc : descendants) {
      if (desc.isFile()) {
        fileCount++;
        totalSize += desc.getSize() != null ? desc.getSize() : 0;
      } else {
        folderCount++;
      }
    }

    return new FolderStats(fileCount, folderCount, totalSize);
  }

  // ==================== 私有方法 ====================

  /**
   * 构建子节点路径。
   *
   * @param parentPath 父目录路径
   * @param name 子节点名称
   * @return 子节点路径（末尾以 "/" 结尾）
   */
  private String buildPath(String parentPath, String name) {
    if (parentPath == null || parentPath.isEmpty()) {
      return "/" + name + "/";
    }
    if (!parentPath.endsWith("/")) {
      parentPath = parentPath + "/";
    }
    return parentPath + name + "/";
  }

  /**
   * 判断 {@code candidate} 是否为 {@code ancestor} 的后代或自身（基于路径前缀）。
   *
   * @param candidate 候选节点
   * @param ancestor 祖先节点
   * @return {@code true} 表示 candidate 是 ancestor 的后代或自身
   */
  private boolean isDescendantOf(FileNodeVO candidate, FileNodeVO ancestor) {
    if (candidate.getId().equals(ancestor.getId())) {
      return true;
    }
    return candidate.getPath() != null
        && ancestor.getPath() != null
        && candidate.getPath().startsWith(ancestor.getPath());
  }

  /** 目录统计信息 */
  public record FolderStats(int fileCount, int folderCount, long totalSize) {}
}
