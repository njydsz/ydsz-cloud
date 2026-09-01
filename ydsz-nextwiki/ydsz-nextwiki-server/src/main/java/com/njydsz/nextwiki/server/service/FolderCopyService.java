package com.njydsz.nextwiki.server.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.nextwiki.domain.dto.FileNodeDTO;
import com.njydsz.nextwiki.domain.dto.FileVersionDTO;
import com.njydsz.nextwiki.domain.repository.FileNodeRepository;
import com.njydsz.nextwiki.domain.repository.FileVersionRepository;
import com.njydsz.nextwiki.domain.service.FileVersionDomainService;
import com.njydsz.nextwiki.domain.service.QuotaDomainService;
import com.njydsz.nextwiki.domain.service.StorageReferenceService;
import com.njydsz.nextwiki.domain.vo.FileNodeVO;
import com.njydsz.nextwiki.server.converter.NextwikiConverter;

/**
 * 文件夹复制服务。
 *
 * <p>处理分批复制文件夹子树的实际逻辑：每批 BATCH_SIZE 个节点在独立事务中完成，避免长事务持锁。
 *
 * <p><b>事务策略：</b>根节点创建由 {@link #createRootFolderNode} 在短事务中完成；后代节点由 {@link
 * #copyDescendantsBatch} 使用 {@code REQUIRES_NEW} 每批独立提交。
 *
 * <p><b>复制顺序：</b>后代节点按 level 升序分页加载、拷贝，确保父节点先于子节点创建， ID 映射可正确重建父子关系。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FolderCopyService {

  /** 每批处理的节点数量 */
  public static final int BATCH_SIZE = 50;

  private final FileNodeRepository fileNodeRepository;
  private final FileVersionRepository versionRepository;
  private final FileVersionDomainService versionDomainService;
  private final StorageReferenceService storageReferenceService;
  private final QuotaDomainService quotaDomainService;
  private final SnowflakeIdGenerator snowflakeIdGenerator;

  /**
   * 短事务创建根文件夹节点。
   *
   * @param sourceFolder 源文件夹节点
   * @param targetParent 目标父目录节点
   * @param targetParentPath 目标父目录路径
   * @param targetParentLevel 目标父目录层级
   * @param userId 操作人 ID
   * @return 新创建的根文件夹节点 ID
   */
  @Transactional(rollbackFor = Exception.class)
  public String createRootFolderNode(
      FileNodeVO sourceFolder,
      FileNodeVO targetParent,
      String targetParentPath,
      int targetParentLevel,
      String userId) {
    String newFolderId = String.valueOf(snowflakeIdGenerator.nextId());

    String folderPath =
        targetParentPath.endsWith("/")
            ? targetParentPath + sourceFolder.getName() + "/"
            : targetParentPath + "/" + sourceFolder.getName() + "/";

    FileNodeDTO newFolderNode =
        FileNodeDTO.builder()
            .id(newFolderId)
            .parentId(targetParent.getId())
            .name(sourceFolder.getName())
            .nodeType(FileNodeVO.TYPE_FOLDER)
            .suffix("")
            .size(0L)
            .storageKey(null)
            .bucketName(null)
            .mimeType(null)
            .path(folderPath)
            .level(targetParentLevel)
            .sort(fileNodeRepository.findChildren(targetParent.getId()).size())
            .currentVersion(0)
            .fileHash(null)
            .thumbnailKey(null)
            .previewReady(false)
            .starred(false)
            .shareStatus("private")
            .createdBy(userId)
            .updatedBy(userId)
            .build();

    fileNodeRepository.save(newFolderNode);

    log.info(
        "[FolderCopyService] 创建根文件夹节点: sourceId={}, newId={}",
        sourceFolder.getId(),
        newFolderId);
    return newFolderId;
  }

  /**
   * 分批复制源文件夹的全部后代节点（每个批次独立事务）。
   *
   * <p>外层仅做编排，每批调用 {@link #copyOneBatch}（{@code REQUIRES_NEW}）。
   *
   * @param sourcePath 源文件夹路径（用于前缀匹配）
   * @param newFolderId 新根文件夹节点 ID
   * @param targetParentNode 目标父目录节点（用于推导基础路径与层级）
   * @param userId 操作人 ID
   * @return 成功复制的节点总数
   */
  public int copyDescendantsBatch(
      String sourcePath, String newFolderId, FileNodeVO targetParentNode, String userId) {
    if (sourcePath == null || sourcePath.isEmpty()) {
      return 0;
    }

    int total = fileNodeRepository.countDescendants(sourcePath);
    if (total == 0) {
      log.info("[FolderCopyService] 源文件夹无后代节点，跳过复制");
      return 0;
    }

    // 用后映射：旧 ID → 新 ID。根节点映射需要由外层在此方法调用前初始化
    Map<String, String> idMapping = new HashMap<>(total * 2);

    int totalCopied = 0;
    int offset = 0;

    while (offset < total) {
      // 分页加载一批后代源节点
      List<FileNodeVO> batch =
          fileNodeRepository.findDescendantsByPage(sourcePath, offset, BATCH_SIZE);
      if (batch.isEmpty()) {
        break;
      }

      // 执行批次复制（独立事务）
      int copied =
          copyOneBatch(batch, newFolderId, targetParentNode, idMapping, userId);
      totalCopied += copied;
      offset += batch.size();

      log.info(
          "[FolderCopyService] 分批复制进度: copied={}, total={}, batch={}",
          totalCopied,
          total,
          batch.size());
    }

    log.info("[FolderCopyService] 分批复制完成: totalCopied={}", totalCopied);
    return totalCopied;
  }

  /**
   * 执行单批节点复制（一个独立事务）。
   *
   * <p>本方法使用 {@code REQUIRES_NEW}，确保每批节点独立提交，事务边界短。
   *
   * @param batchSourceNodes 本批源节点列表
   * @param newRootFolderId 新根文件夹节点 ID
   * @param targetParentNode 目标父目录节点
   * @param idMapping 旧 ID → 新 ID 映射（跨批次持久化）
   * @param userId 操作人 ID
   * @return 实际复制的节点数量
   */
  @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
  public int copyOneBatch(
      List<FileNodeVO> batchSourceNodes,
      String newRootFolderId,
      FileNodeVO targetParentNode,
      Map<String, String> idMapping,
      String userId) {
    if (batchSourceNodes == null || batchSourceNodes.isEmpty()) {
      return 0;
    }

    List<FileNodeDTO> newNodes = new ArrayList<>(batchSourceNodes.size());

    for (FileNodeVO source : batchSourceNodes) {
      String newId = String.valueOf(snowflakeIdGenerator.nextId());
      idMapping.put(source.getId(), newId);

      // 计算新父节点 ID
      String newParentId = resolveNewParentId(source.getParentId(), newRootFolderId, idMapping);

      // 推导新路径与层级
      FileNodeVO newParentNode = fileNodeRepository.findById(newParentId).orElse(null);
      if (newParentNode == null) {
        // 如果父节点尚未在此批生成，且不在映射中，跳过此节点（下批再处理）
        // 但分页按 level 升序，理论上不会遇到
        log.warn(
            "[FolderCopyService] 父节点不存在，跳过: sourceId={}, parentId={}",
            source.getId(),
            source.getParentId());
        continue;
      }

      String newParentPath = newParentNode.getPath() != null ? newParentNode.getPath() : "/";
      int newLevel = newParentNode.getLevel() != null ? newParentNode.getLevel() + 1 : 1;
      String nodePath =
          newParentPath.endsWith("/")
              ? newParentPath + source.getName() + "/"
              : newParentPath + "/" + source.getName() + "/";

      FileNodeDTO newNode =
          FileNodeDTO.builder()
              .id(newId)
              .parentId(newParentId)
              .name(source.getName())
              .nodeType(source.getNodeType())
              .suffix(source.getSuffix())
              .size(source.getSize())
              .storageKey(source.getStorageKey())
              .bucketName(source.getBucketName())
              .mimeType(source.getMimeType())
              .path(nodePath)
              .level(newLevel)
              .sort(0)
              .currentVersion(source.getCurrentVersion())
              .fileHash(source.getFileHash())
              .thumbnailKey(source.getThumbnailKey())
              .previewReady(source.getPreviewReady())
              .starred(false)
              .shareStatus("private")
              .createdBy(userId)
              .updatedBy(userId)
              .build();

      newNodes.add(newNode);

      // 文件引用计数 +1
      if (source.isFile() && source.getStorageKey() != null) {
        storageReferenceService.increment(source.getStorageKey());
      }
    }

    // 批量保存节点
    if (!newNodes.isEmpty()) {
      fileNodeRepository.saveBatch(newNodes);
    }

    // 批量创建版本引用（for files only）
    for (int i = 0; i < newNodes.size(); i++) {
      FileNodeDTO newNode = newNodes.get(i);
      FileNodeVO source = batchSourceNodes.get(i);
      if (source.isFile()) {
        List<FileVersionDTO> existingVersionDTOs = NextwikiConverter.INSTANT.versionListToDTO(
            versionRepository.findByFileNodeId(newNode.getId()));
        // 将DTO转换为VO用于版本创建
        FileNodeVO newNodeVO = NextwikiConverter.INSTANT.dtoToVO(newNode);
        FileVersionDomainService.VersionCreateResult versionResult =
            versionDomainService.createVersion(
                newNodeVO,
                existingVersionDTOs,
                source.getStorageKey(),
                source.getSize(),
                source.getFileHash(),
                source.getMimeType(),
                "文件夹复制",
                userId);
        versionRepository.setActiveVersion(newNode.getId(), -1);
        versionRepository.save(versionResult.newVersion());
        fileNodeRepository.update(NextwikiConverter.INSTANT.toDTO(versionResult.updatedFileNode()));
      }
    }

    return newNodes.size();
  }

  /**
   * 解析源父节点 ID 对应的新父节点 ID。
   *
   * @param sourceParentId 源父节点 ID
   * @param newRootFolderId 新根文件夹 ID
   * @param idMapping 旧 ID → 新 ID 映射
   * @return 新父节点 ID；若映射中不存在，回退到 {@code newRootFolderId}
   */
  private String resolveNewParentId(
      String sourceParentId, String newRootFolderId, Map<String, String> idMapping) {
    if (sourceParentId == null) {
      return newRootFolderId;
    }
    return idMapping.getOrDefault(sourceParentId, newRootFolderId);
  }
}
