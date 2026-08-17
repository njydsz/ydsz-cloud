package com.njydsz.nextwiki.domain.service;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.nextwiki.domain.entity.FileNode;
import com.njydsz.nextwiki.domain.entity.FileVersion;
import com.njydsz.nextwiki.domain.enums.NextwikiExceptionCode;

/**
 * 文件版本领域服务
 *
 * <p>管理文件版本生命周期：创建版本、版本回滚、版本清理。
 *
 * <p><b>版本保留策略：</b>
 *
 * <ul>
 *   <li>默认保留最近 {@value #MAX_VERSIONS} 个版本
 *   <li>超过限制时由调用方根据 {@link #findVersionsToCleanup} 返回的列表执行清理
 * </ul>
 *
 * <p><b>设计原则：</b>本服务仅包含纯领域逻辑，不直接依赖 Repository 接口。 所有数据由 server 层查询后传入，本服务返回的结果由 server 层负责持久化。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileVersionDomainService {

  /** 分布式 ID 生成器 */
  private final SnowflakeIdGenerator snowflakeIdGenerator;

  /** 最大保留版本数 */
  private static final int MAX_VERSIONS = 20;

  /**
   * 创建新版本（文件上传/更新时调用）
   *
   * <p>由 server 层查询 {@link FileNode} 和现有版本列表后传入，本方法仅执行领域逻辑： 计算下一版本号、构建版本记录、更新文件节点当前版本信息。
   *
   * <p>返回的 {@link VersionCreateResult} 包含待持久化的新版本和更新后的文件节点， 由 server 层在同一事务中完成持久化与旧版本失效标记。
   *
   * @param fileNode 文件节点（由 server 层查询传入，不可为 {@code null}）
   * @param existingVersions 当前所有版本列表（由 server 层查询传入，可为空，不可为 {@code null}）
   * @param storageKey 存储对象键
   * @param size 文件大小（字节）
   * @param fileHash 文件 SHA-256 哈希
   * @param mimeType MIME 类型
   * @param remark 版本备注
   * @param userId 操作人 ID
   * @return 包含新版本和更新后文件节点的结果对象
   * @throws BusinessException 文件节点不存在或不是文件类型时抛出
   */
  public VersionCreateResult createVersion(
      FileNode fileNode,
      List<FileVersion> existingVersions,
      String storageKey,
      Long size,
      String fileHash,
      String mimeType,
      String remark,
      String userId) {
    if (fileNode == null || !fileNode.isFile()) {
      throw BusinessException.of(NextwikiExceptionCode.FILE_NOT_FOUND)
          .data("fileNodeId", fileNode != null ? fileNode.getId() : null);
    }

    // 取当前最大版本号
    int nextVersion =
        existingVersions.stream()
                .mapToInt(v -> v.getVersionNumber() != null ? v.getVersionNumber() : 0)
                .max()
                .orElse(0)
            + 1;

    FileVersion version =
        FileVersion.builder()
            .id(String.valueOf(snowflakeIdGenerator.nextId()).replace("-", ""))
            .fileNodeId(fileNode.getId())
            .versionNumber(nextVersion)
            .storageKey(storageKey)
            .size(size)
            .fileHash(fileHash)
            .mimeType(mimeType)
            .remark(remark)
            .changeType(nextVersion == 1 ? "create" : "update")
            .active(true)
            .revision(0)
            .deleted(0)
            .build();

    version.setCreatedBy(userId);
    version.setUpdatedBy(userId);

    // 更新文件节点的当前版本信息
    fileNode.setCurrentVersion(nextVersion);
    fileNode.setStorageKey(storageKey);
    fileNode.setSize(size);
    fileNode.setFileHash(fileHash);
    fileNode.setMimeType(mimeType);
    fileNode.setUpdatedBy(userId);

    log.info(
        "[FileVersionDomainService] 创建版本: fileNodeId={}, version={}",
        fileNode.getId(),
        nextVersion);
    return new VersionCreateResult(version, fileNode);
  }

  /**
   * 回滚到指定版本
   *
   * <p>由 server 层查询 {@link FileNode}、目标版本和现有版本列表后传入，本方法仅执行领域逻辑： 校验目标版本、计算下一版本号、构建回滚版本记录、更新文件节点。
   *
   * <p>返回的 {@link VersionRollbackResult} 包含待持久化的新版本、更新后的文件节点和目标版本号， 由 server 层在同一事务中完成持久化与事件发布。
   *
   * @param fileNode 文件节点（由 server 层查询传入，不可为 {@code null}）
   * @param targetVersion 目标版本（由 server 层查询传入，不可为 {@code null}）
   * @param existingVersions 当前所有版本列表（由 server 层查询传入，可为空，不可为 {@code null}）
   * @param userId 操作人 ID
   * @return 包含新版本、更新后文件节点和目标版本号的结果对象
   * @throws BusinessException 文件节点不存在或目标版本不存在时抛出
   */
  public VersionRollbackResult rollback(
      FileNode fileNode,
      FileVersion targetVersion,
      List<FileVersion> existingVersions,
      String userId) {
    if (fileNode == null || !fileNode.isFile()) {
      throw BusinessException.of(NextwikiExceptionCode.FILE_NOT_FOUND)
          .data("fileNodeId", fileNode != null ? fileNode.getId() : null);
    }

    if (targetVersion == null) {
      throw BusinessException.of(NextwikiExceptionCode.VERSION_NOT_FOUND);
    }

    // 计算下一版本号
    int nextVersion =
        existingVersions.stream()
                .mapToInt(v -> v.getVersionNumber() != null ? v.getVersionNumber() : 0)
                .max()
                .orElse(0)
            + 1;

    FileVersion rollbackVersion =
        FileVersion.builder()
            .id(String.valueOf(snowflakeIdGenerator.nextId()).replace("-", ""))
            .fileNodeId(fileNode.getId())
            .versionNumber(nextVersion)
            .storageKey(targetVersion.getStorageKey())
            .size(targetVersion.getSize())
            .fileHash(targetVersion.getFileHash())
            .mimeType(targetVersion.getMimeType())
            .remark("回滚到版本 " + targetVersion.getVersionNumber())
            .changeType("rollback")
            .active(true)
            .revision(0)
            .deleted(0)
            .build();

    rollbackVersion.setCreatedBy(userId);
    rollbackVersion.setUpdatedBy(userId);

    // 更新文件节点
    fileNode.setCurrentVersion(nextVersion);
    fileNode.setStorageKey(targetVersion.getStorageKey());
    fileNode.setSize(targetVersion.getSize());
    fileNode.setFileHash(targetVersion.getFileHash());
    fileNode.setMimeType(targetVersion.getMimeType());
    fileNode.setUpdatedBy(userId);

    log.info(
        "[FileVersionDomainService] 版本回滚: fileNodeId={}, targetVersion={}, newVersion={}",
        fileNode.getId(),
        targetVersion.getVersionNumber(),
        nextVersion);
    return new VersionRollbackResult(rollbackVersion, fileNode, targetVersion.getVersionNumber());
  }

  /**
   * 查找需要清理的超限版本
   *
   * <p>根据版本保留策略（保留最近 {@value #MAX_VERSIONS} 个版本），返回应删除的旧版本列表。 返回列表已按版本号升序排列（最旧的在前），server 层据此执行批量删除。
   *
   * @param allVersions 所有版本列表（包含新创建的，不可为 {@code null}）
   * @return 需要删除的版本列表；若未超限返回空列表
   */
  public List<FileVersion> findVersionsToCleanup(List<FileVersion> allVersions) {
    if (allVersions.size() <= MAX_VERSIONS) {
      return Collections.emptyList();
    }

    // 保留最近 MAX_VERSIONS 个版本，删除其余（最旧的）
    return allVersions.stream()
        .sorted(Comparator.comparing(v -> v.getVersionNumber() != null ? v.getVersionNumber() : 0))
        .limit(allVersions.size() - MAX_VERSIONS)
        .collect(Collectors.toList());
  }

  // ==================== 结果对象 ====================

  /**
   * 创建版本的结果
   *
   * @param newVersion 待持久化的新版本实体
   * @param updatedFileNode 待持久化的更新后文件节点
   */
  public record VersionCreateResult(FileVersion newVersion, FileNode updatedFileNode) {}

  /**
   * 版本回滚的结果
   *
   * @param newVersion 待持久化的新版本实体（回滚版本）
   * @param updatedFileNode 待持久化的更新后文件节点
   * @param targetVersionNumber 目标版本号（用于事件发布）
   */
  public record VersionRollbackResult(
      FileVersion newVersion, FileNode updatedFileNode, Integer targetVersionNumber) {}
}
