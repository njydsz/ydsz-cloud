package com.njydsz.nextwiki.domain.converter;

import java.util.List;
import java.util.stream.Collectors;

import com.njydsz.nextwiki.domain.dto.FileAclDTO;
import com.njydsz.nextwiki.domain.dto.FileCommentDTO;
import com.njydsz.nextwiki.domain.dto.FileNodeDTO;
import com.njydsz.nextwiki.domain.dto.FileTagDTO;
import com.njydsz.nextwiki.domain.dto.FileVersionDTO;
import com.njydsz.nextwiki.domain.dto.SearchIndexDTO;
import com.njydsz.nextwiki.domain.dto.ShareAccessLogDTO;
import com.njydsz.nextwiki.domain.dto.ShareLinkDTO;
import com.njydsz.nextwiki.domain.dto.ShareRecipientDTO;
import com.njydsz.nextwiki.domain.dto.StorageQuotaDTO;
import com.njydsz.nextwiki.domain.dto.TagDTO;
import com.njydsz.nextwiki.domain.dto.TrashItemDTO;
import com.njydsz.nextwiki.domain.entity.FileAcl;
import com.njydsz.nextwiki.domain.entity.FileComment;
import com.njydsz.nextwiki.domain.entity.FileNode;
import com.njydsz.nextwiki.domain.entity.FileTag;
import com.njydsz.nextwiki.domain.entity.FileVersion;
import com.njydsz.nextwiki.domain.entity.SearchIndex;
import com.njydsz.nextwiki.domain.entity.ShareAccessLog;
import com.njydsz.nextwiki.domain.entity.ShareLink;
import com.njydsz.nextwiki.domain.entity.ShareRecipient;
import com.njydsz.nextwiki.domain.entity.StorageQuota;
import com.njydsz.nextwiki.domain.entity.Tag;
import com.njydsz.nextwiki.domain.entity.TrashItem;
import com.njydsz.nextwiki.domain.vo.FileAclVO;
import com.njydsz.nextwiki.domain.vo.FileCommentVO;
import com.njydsz.nextwiki.domain.vo.FileNodeVO;
import com.njydsz.nextwiki.domain.vo.FileTagVO;
import com.njydsz.nextwiki.domain.vo.FileVersionVO;
import com.njydsz.nextwiki.domain.vo.SearchIndexVO;
import com.njydsz.nextwiki.domain.vo.ShareAccessLogVO;
import com.njydsz.nextwiki.domain.vo.ShareLinkVO;
import com.njydsz.nextwiki.domain.vo.ShareRecipientVO;
import com.njydsz.nextwiki.domain.vo.StorageQuotaVO;
import com.njydsz.nextwiki.domain.vo.TagVO;
import com.njydsz.nextwiki.domain.vo.TrashItemVO;

/**
 * NextWiki 实体 ↔ VO 转换器。
 *
 * <p>采用单例 + 手动映射方式，避免引入 MapStruct 编译期依赖。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @deprecated 违反云顶编码规范§34.2.2 — Converter 只能放在 infra 层。
 *             <p><b>DDD 合规路径：</b>
 *             <ul>
 *               <li>Repository 接口应直接接受/返回 VO（domain 层定义）
 *               <li>infra 层 MapStruct Converter 负责 DO ↔ VO 转换
 *               <li>server 层直接使用 VO，无需 VO ↔ DTO 转换
 *             </ul>
 *             <p>待 Repository 接口重构为 VO 入参/出参后删除本类。
 */
@Deprecated
public final class NextwikiConverter {

  /** 单例实例 */
  public static final NextwikiConverter INSTANT = new NextwikiConverter();

  private NextwikiConverter() {}

  /**
   * 将 {@link FileNodeVO} 视图对象转换为 {@link FileNodeDTO} 数据传输对象。
   *
   * <p>用于将 Repository 返回的 VO 转回 DTO，以便调用 Repository 的 CUD 方法。
   *
   * @param vo 文件节点 VO，为 {@code null} 时返回 {@code null}
   * @return 文件节点 DTO，或 {@code null}
   */
  public FileNodeDTO toDTO(FileNodeVO vo) {
    if (vo == null) {
      return null;
    }
    return FileNodeDTO.builder()
        .id(vo.getId())
        .parentId(vo.getParentId())
        .name(vo.getName())
        .nodeType(vo.getNodeType())
        .suffix(vo.getSuffix())
        .size(vo.getSize())
        .mimeType(vo.getMimeType())
        .storageKey(vo.getStorageKey())
        .bucketName(vo.getBucketName())
        .storageClass(vo.getStorageClass())
        .fileHash(vo.getFileHash())
        .path(vo.getPath())
        .level(vo.getLevel())
        .sort(vo.getSort())
        .currentVersion(vo.getCurrentVersion())
        .thumbnailKey(vo.getThumbnailKey())
        .previewReady(vo.getPreviewReady())
        .starred(vo.getStarred())
        .status(vo.getStatus())
        .shareStatus(vo.getShareStatus())
        .createdBy(vo.getCreatedBy())
        .updatedBy(vo.getUpdatedBy())
        .build();
  }

  /**
   * 将 {@link FileNodeDTO} 数据传输对象转换为 {@link FileNodeVO} 视图对象。
   *
   * <p>用于将 DTO 转回 VO，以便调用领域服务方法。
   *
   * @param dto 文件节点 DTO，为 {@code null} 时返回 {@code null}
   * @return 文件节点 VO，或 {@code null}
   */
  public FileNodeVO dtoToVO(FileNodeDTO dto) {
    if (dto == null) {
      return null;
    }
    return FileNodeVO.builder()
        .id(dto.getId())
        .parentId(dto.getParentId())
        .name(dto.getName())
        .nodeType(dto.getNodeType())
        .suffix(dto.getSuffix())
        .size(dto.getSize())
        .mimeType(dto.getMimeType())
        .storageKey(dto.getStorageKey())
        .bucketName(dto.getBucketName())
        .storageClass(dto.getStorageClass())
        .fileHash(dto.getFileHash())
        .path(dto.getPath())
        .level(dto.getLevel())
        .sort(dto.getSort())
        .currentVersion(dto.getCurrentVersion())
        .thumbnailKey(dto.getThumbnailKey())
        .previewReady(dto.getPreviewReady())
        .starred(dto.getStarred())
        .status(dto.getStatus())
        .shareStatus(dto.getShareStatus())
        .createdBy(dto.getCreatedBy())
        .updatedBy(dto.getUpdatedBy())
        .build();
  }

  /**
   * 将 {@link FileVersionVO} 视图对象转换为 {@link FileVersionDTO} 数据传输对象。
   *
   * <p>用于将 Repository 返回的版本 VO 转回 DTO，以便调用领域服务方法。
   *
   * @param vo 文件版本 VO，为 {@code null} 时返回 {@code null}
   * @return 文件版本 DTO，或 {@code null}
   */
  public FileVersionDTO versionToDTO(FileVersionVO vo) {
    if (vo == null) {
      return null;
    }
    return FileVersionDTO.builder()
        .id(vo.getId())
        .fileNodeId(vo.getFileNodeId())
        .versionNumber(vo.getVersionNumber())
        .storageKey(vo.getStorageKey())
        .size(vo.getSize())
        .fileHash(vo.getFileHash())
        .mimeType(vo.getMimeType())
        .remark(vo.getRemark())
        .changeType(vo.getChangeType())
        .active(vo.getActive())
        .createdBy(vo.getCreatedBy())
        .createdAt(vo.getCreatedAt())
        .build();
  }

  /**
   * 将 {@link FileVersionDTO} 数据传输对象转换为 {@link FileVersionVO} 视图对象。
   *
   * @param dto 文件版本 DTO，为 {@code null} 时返回 {@code null}
   * @return 文件版本 VO，或 {@code null}
   */
  public FileVersionVO versionToVO(FileVersionDTO dto) {
    if (dto == null) {
      return null;
    }
    return FileVersionVO.builder()
        .id(dto.getId())
        .fileNodeId(dto.getFileNodeId())
        .versionNumber(dto.getVersionNumber())
        .storageKey(dto.getStorageKey())
        .size(dto.getSize())
        .fileHash(dto.getFileHash())
        .mimeType(dto.getMimeType())
        .remark(dto.getRemark())
        .changeType(dto.getChangeType())
        .active(dto.getActive())
        .createdBy(dto.getCreatedBy())
        .createdAt(dto.getCreatedAt())
        .build();
  }

  /**
   * 将 {@link FileVersionVO} 列表批量转换为 {@link FileVersionDTO} 列表。
   *
   * @param vos 文件版本 VO 列表
   * @return 文件版本 DTO 列表
   */
  public List<FileVersionDTO> versionListToDTO(List<FileVersionVO> vos) {
    if (vos == null || vos.isEmpty()) {
      return List.of();
    }
    return vos.stream().map(this::versionToDTO).collect(Collectors.toList());
  }

  /**
   * 将 {@link FileVersionDTO} 列表批量转换为 {@link FileVersionVO} 列表。
   *
   * @param dtos 文件版本 DTO 列表
   * @return 文件版本 VO 列表
   */
  public List<FileVersionVO> versionListToVO(List<FileVersionDTO> dtos) {
    if (dtos == null || dtos.isEmpty()) {
      return List.of();
    }
    return dtos.stream().map(this::versionToVO).collect(Collectors.toList());
  }

  /**
   * 将 {@link ShareLinkVO} 列表批量转换为 {@link ShareLinkDTO} 列表。
   *
   * <p>用于分享链接到期提醒等场景：Repository 返回 VO 列表，领域服务需要 DTO 入参。
   *
   * @param vos 分享链接 VO 列表
   * @return 分享链接 DTO 列表；入参为 {@code null} 或空时返回空列表
   */
  public List<ShareLinkDTO> shareLinkListToDTO(List<ShareLinkVO> vos) {
    if (vos == null || vos.isEmpty()) {
      return List.of();
    }
    return vos.stream().map(this::shareLinkToDTO).collect(Collectors.toList());
  }

  /**
   * 将单个 {@link ShareLinkVO} 转换为 {@link ShareLinkDTO}。
   *
   * @param vo 分享链接 VO，为 {@code null} 时返回 {@code null}
   * @return 分享链接 DTO，或 {@code null}
   */
  public ShareLinkDTO shareLinkToDTO(ShareLinkVO vo) {
    if (vo == null) {
      return null;
    }
    ShareLinkDTO dto = new ShareLinkDTO();
    dto.setId(vo.getId());
    dto.setFileNodeId(vo.getFileNodeId());
    dto.setShareCode(vo.getShareCode());
    dto.setExtractCode(vo.getExtractCode());
    dto.setShareType(vo.getShareType());
    dto.setExpireTime(vo.getExpireTime());
    dto.setMaxAccessCount(vo.getMaxAccessCount());
    dto.setAccessCount(vo.getAccessCount());
    return dto;
  }

  /**
   * 将 {@link TrashItemVO} 列表批量转换为 {@link TrashItemDTO} 列表。
   *
   * <p>用于回收站清理定时任务：Repository 返回 VO 列表，领域服务需要 DTO 入参。
   *
   * @param vos 回收站条目 VO 列表
   * @return 回收站条目 DTO 列表；入参为 {@code null} 或空时返回空列表
   */
  public List<TrashItemDTO> trashItemListToDTO(List<TrashItemVO> vos) {
    if (vos == null || vos.isEmpty()) {
      return List.of();
    }
    return vos.stream().map(this::trashItemToDTO).collect(Collectors.toList());
  }

  /**
   * 将单个 {@link TrashItemVO} 转换为 {@link TrashItemDTO}。
   *
   * @param vo 回收站条目 VO，为 {@code null} 时返回 {@code null}
   * @return 回收站条目 DTO，或 {@code null}
   */
  public TrashItemDTO trashItemToDTO(TrashItemVO vo) {
    if (vo == null) {
      return null;
    }
    TrashItemDTO dto = new TrashItemDTO();
    dto.setId(vo.getId());
    dto.setFileNodeId(vo.getFileNodeId());
    dto.setOriginalName(vo.getOriginalName());
    dto.setOriginalPath(vo.getOriginalPath());
    dto.setOriginalParentId(vo.getOriginalParentId());
    dto.setNodeType(vo.getNodeType());
    dto.setSize(vo.getSize());
    dto.setDeletedTime(vo.getDeletedTime());
    dto.setPurgeTime(vo.getPurgeTime());
    dto.setStatus(vo.getStatus());
    dto.setCreatedBy(vo.getCreatedBy());
    return dto;
  }

  // ==================== FileAcl 转换 ====================

  public FileAclVO entityToVO(FileAcl entity) {
    if (entity == null) {
      return null;
    }
    return FileAclVO.builder()
        .id(entity.getId())
        .fileNodeId(entity.getFileNodeId())
        .granteeType(entity.getGranteeType())
        .granteeId(entity.getGranteeId())
        .permissionMask(entity.getPermissionMask())
        .inherited(entity.getInherited())
        .owner(entity.getOwner())
        .createdBy(entity.getCreatedBy())
        .createdAt(entity.getCreatedAt())
        .build();
  }

  public FileAcl dtoToEntity(FileAclDTO dto) {
    if (dto == null) {
      return null;
    }
    return FileAcl.builder()
        .id(dto.getId())
        .fileNodeId(dto.getFileNodeId())
        .granteeType(dto.getGranteeType())
        .granteeId(dto.getGranteeId())
        .permissionMask(dto.getPermissionMask())
        .inherited(dto.getInherited())
        .owner(dto.getOwner())
        .createdBy(dto.getCreatedBy())
        .build();
  }

  public List<FileAclVO> fileAclListToVO(List<FileAcl> entities) {
    if (entities == null || entities.isEmpty()) {
      return List.of();
    }
    return entities.stream().map(this::entityToVO).collect(Collectors.toList());
  }

  public List<FileAcl> fileAclDtosToEntities(List<FileAclDTO> dtos) {
    if (dtos == null || dtos.isEmpty()) {
      return List.of();
    }
    return dtos.stream().map(this::dtoToEntity).collect(Collectors.toList());
  }

  // ==================== FileNode 转换（补充） ====================

  /**
   * 将 {@link FileNodeDTO} 转换为 {@link FileNode} 实体。
   *
   * @param dto 文件节点 DTO，为 {@code null} 时返回 {@code null}
   * @return 文件节点实体，或 {@code null}
   */
  public FileNode dtoToEntity(FileNodeDTO dto) {
    if (dto == null) {
      return null;
    }
    return FileNode.builder()
        .id(dto.getId())
        .parentId(dto.getParentId())
        .name(dto.getName())
        .nodeType(dto.getNodeType())
        .suffix(dto.getSuffix())
        .size(dto.getSize())
        .mimeType(dto.getMimeType())
        .storageKey(dto.getStorageKey())
        .bucketName(dto.getBucketName())
        .storageClass(dto.getStorageClass())
        .fileHash(dto.getFileHash())
        .path(dto.getPath())
        .level(dto.getLevel())
        .sort(dto.getSort())
        .currentVersion(dto.getCurrentVersion())
        .thumbnailKey(dto.getThumbnailKey())
        .previewReady(dto.getPreviewReady())
        .starred(dto.getStarred())
        .status(dto.getStatus())
        .shareStatus(dto.getShareStatus())
        .createdBy(dto.getCreatedBy())
        .updatedBy(dto.getUpdatedBy())
        .build();
  }

  /**
   * 将 {@link FileNode} 实体转换为 {@link FileNodeVO}。
   *
   * @param entity 文件节点实体，为 {@code null} 时返回 {@code null}
   * @return 文件节点 VO，或 {@code null}
   */
  public FileNodeVO entityToVO(FileNode entity) {
    if (entity == null) {
      return null;
    }
    return FileNodeVO.builder()
        .id(entity.getId())
        .parentId(entity.getParentId())
        .name(entity.getName())
        .nodeType(entity.getNodeType())
        .suffix(entity.getSuffix())
        .size(entity.getSize())
        .mimeType(entity.getMimeType())
        .storageKey(entity.getStorageKey())
        .bucketName(entity.getBucketName())
        .storageClass(entity.getStorageClass())
        .fileHash(entity.getFileHash())
        .path(entity.getPath())
        .level(entity.getLevel())
        .sort(entity.getSort())
        .currentVersion(entity.getCurrentVersion())
        .thumbnailKey(entity.getThumbnailKey())
        .previewReady(entity.getPreviewReady())
        .starred(entity.getStarred())
        .status(entity.getStatus())
        .shareStatus(entity.getShareStatus())
        .createdBy(entity.getCreatedBy())
        .updatedBy(entity.getUpdatedBy())
        .build();
  }

  /**
   * 将 {@link FileNodeDTO} 转换为保留 ID 的 {@link FileNode} 实体（用于更新场景）。
   *
   * @param dto 文件节点 DTO，为 {@code null} 时返回 {@code null}
   * @return 保留 ID 的文件节点实体，或 {@code null}
   */
  public FileNode dtoToEntityWithId(FileNodeDTO dto) {
    if (dto == null) {
      return null;
    }
    return FileNode.builder()
        .id(dto.getId())
        .parentId(dto.getParentId())
        .name(dto.getName())
        .nodeType(dto.getNodeType())
        .suffix(dto.getSuffix())
        .size(dto.getSize())
        .mimeType(dto.getMimeType())
        .storageKey(dto.getStorageKey())
        .bucketName(dto.getBucketName())
        .storageClass(dto.getStorageClass())
        .fileHash(dto.getFileHash())
        .path(dto.getPath())
        .level(dto.getLevel())
        .sort(dto.getSort())
        .currentVersion(dto.getCurrentVersion())
        .thumbnailKey(dto.getThumbnailKey())
        .previewReady(dto.getPreviewReady())
        .starred(dto.getStarred())
        .status(dto.getStatus())
        .shareStatus(dto.getShareStatus())
        .createdBy(dto.getCreatedBy())
        .updatedBy(dto.getUpdatedBy())
        .build();
  }

  /**
   * 将 {@link FileNode} 实体列表批量转换为 {@link FileNodeVO} 列表。
   *
   * @param entities 文件节点实体列表
   * @return 文件节点 VO 列表；入参为 {@code null} 或空时返回空列表
   */
  public List<FileNodeVO> fileNodeListToVO(List<FileNode> entities) {
    if (entities == null || entities.isEmpty()) {
      return List.of();
    }
    return entities.stream().map(this::entityToVO).collect(Collectors.toList());
  }

  /**
   * 将 {@link FileNodeDTO} 列表批量转换为 {@link FileNode} 实体列表。
   *
   * @param dtos 文件节点 DTO 列表
   * @return 文件节点实体列表；入参为 {@code null} 或空时返回空列表
   */
  public List<FileNode> fileNodeDtosToEntities(List<FileNodeDTO> dtos) {
    if (dtos == null || dtos.isEmpty()) {
      return List.of();
    }
    return dtos.stream().map(this::dtoToEntity).collect(Collectors.toList());
  }

  // ==================== FileComment 转换 ====================

  /**
   * 将 {@link FileCommentDTO} 转换为 {@link FileComment} 实体。
   *
   * @param dto 文件评论 DTO，为 {@code null} 时返回 {@code null}
   * @return 文件评论实体，或 {@code null}
   */
  public FileComment dtoToEntity(FileCommentDTO dto) {
    if (dto == null) {
      return null;
    }
    return FileComment.builder()
        .fileNodeId(dto.getFileNodeId())
        .content(dto.getContent())
        .parentCommentId(dto.getParentCommentId())
        .resolved(dto.getResolved())
        .position(dto.getPosition())
        .edited(dto.getEdited())
        .build();
  }

  /**
   * 将 {@link FileComment} 实体转换为 {@link FileCommentVO}。
   *
   * @param entity 文件评论实体，为 {@code null} 时返回 {@code null}
   * @return 文件评论 VO，或 {@code null}
   */
  public FileCommentVO entityToVO(FileComment entity) {
    if (entity == null) {
      return null;
    }
    return FileCommentVO.builder()
        .id(entity.getId())
        .fileNodeId(entity.getFileNodeId())
        .content(entity.getContent())
        .parentCommentId(entity.getParentCommentId())
        .resolved(entity.getResolved())
        .position(entity.getPosition())
        .edited(entity.getEdited())
        .createdBy(entity.getCreatedBy())
        .createdAt(entity.getCreatedAt())
        .build();
  }

  /**
   * 将 {@link FileCommentDTO} 转换为保留 ID 的 {@link FileComment} 实体（用于更新场景）。
   *
   * @param dto 文件评论 DTO，为 {@code null} 时返回 {@code null}
   * @return 保留 ID 的文件评论实体，或 {@code null}
   */
  public FileComment dtoToEntityWithId(FileCommentDTO dto) {
    if (dto == null) {
      return null;
    }
    return FileComment.builder()
        .id(dto.getId())
        .fileNodeId(dto.getFileNodeId())
        .content(dto.getContent())
        .parentCommentId(dto.getParentCommentId())
        .resolved(dto.getResolved())
        .position(dto.getPosition())
        .edited(dto.getEdited())
        .build();
  }

  /**
   * 将 {@link FileComment} 实体列表批量转换为 {@link FileCommentVO} 列表。
   *
   * @param entities 文件评论实体列表
   * @return 文件评论 VO 列表；入参为 {@code null} 或空时返回空列表
   */
  public List<FileCommentVO> fileCommentListToVO(List<FileComment> entities) {
    if (entities == null || entities.isEmpty()) {
      return List.of();
    }
    return entities.stream().map(this::entityToVO).collect(Collectors.toList());
  }

  // ==================== FileVersion 转换（补充） ====================

  /**
   * 将 {@link FileVersionDTO} 转换为 {@link FileVersion} 实体。
   *
   * @param dto 文件版本 DTO，为 {@code null} 时返回 {@code null}
   * @return 文件版本实体，或 {@code null}
   */
  public FileVersion dtoToEntity(FileVersionDTO dto) {
    if (dto == null) {
      return null;
    }
    return FileVersion.builder()
        .fileNodeId(dto.getFileNodeId())
        .versionNumber(dto.getVersionNumber())
        .storageKey(dto.getStorageKey())
        .size(dto.getSize())
        .fileHash(dto.getFileHash())
        .mimeType(dto.getMimeType())
        .remark(dto.getRemark())
        .changeType(dto.getChangeType())
        .active(dto.getActive())
        .createdBy(dto.getCreatedBy())
        .build();
  }

  /**
   * 将 {@link FileVersion} 实体转换为 {@link FileVersionVO}。
   *
   * @param entity 文件版本实体，为 {@code null} 时返回 {@code null}
   * @return 文件版本 VO，或 {@code null}
   */
  public FileVersionVO entityToVO(FileVersion entity) {
    if (entity == null) {
      return null;
    }
    return FileVersionVO.builder()
        .id(entity.getId())
        .fileNodeId(entity.getFileNodeId())
        .versionNumber(entity.getVersionNumber())
        .storageKey(entity.getStorageKey())
        .size(entity.getSize())
        .fileHash(entity.getFileHash())
        .mimeType(entity.getMimeType())
        .remark(entity.getRemark())
        .changeType(entity.getChangeType())
        .active(entity.getActive())
        .createdBy(entity.getCreatedBy())
        .createdAt(entity.getCreatedAt())
        .build();
  }

  /**
   * 将 {@link FileVersionDTO} 转换为保留 ID 的 {@link FileVersion} 实体（用于更新场景）。
   *
   * @param dto 文件版本 DTO，为 {@code null} 时返回 {@code null}
   * @return 保留 ID 的文件版本实体，或 {@code null}
   */
  public FileVersion dtoToEntityWithId(FileVersionDTO dto) {
    if (dto == null) {
      return null;
    }
    return FileVersion.builder()
        .id(dto.getId())
        .fileNodeId(dto.getFileNodeId())
        .versionNumber(dto.getVersionNumber())
        .storageKey(dto.getStorageKey())
        .size(dto.getSize())
        .fileHash(dto.getFileHash())
        .mimeType(dto.getMimeType())
        .remark(dto.getRemark())
        .changeType(dto.getChangeType())
        .active(dto.getActive())
        .createdBy(dto.getCreatedBy())
        .build();
  }

  /**
   * 将 {@link FileVersion} 实体列表批量转换为 {@link FileVersionVO} 列表。
   *
   * @param entities 文件版本实体列表
   * @return 文件版本 VO 列表；入参为 {@code null} 或空时返回空列表
   */
  public List<FileVersionVO> fileVersionListToVO(List<FileVersion> entities) {
    if (entities == null || entities.isEmpty()) {
      return List.of();
    }
    return entities.stream().map(this::entityToVO).collect(Collectors.toList());
  }

  // ==================== SearchIndex 转换 ====================

  /**
   * 将 {@link SearchIndexDTO} 转换为 {@link SearchIndex} 实体。
   *
   * @param dto 搜索索引 DTO，为 {@code null} 时返回 {@code null}
   * @return 搜索索引实体，或 {@code null}
   */
  public SearchIndex dtoToEntity(SearchIndexDTO dto) {
    if (dto == null) {
      return null;
    }
    return SearchIndex.builder()
        .id(dto.getId())
        .fileNodeId(dto.getFileNodeId())
        .name(dto.getName())
        .path(dto.getPath())
        .content(dto.getContent())
        .suffix(dto.getSuffix())
        .mimeType(dto.getMimeType())
        .size(dto.getSize())
        .tags(dto.getTags())
        .build();
  }

  /**
   * 将 {@link SearchIndex} 实体转换为 {@link SearchIndexVO}。
   *
   * @param entity 搜索索引实体，为 {@code null} 时返回 {@code null}
   * @return 搜索索引 VO，或 {@code null}
   */
  public SearchIndexVO entityToVO(SearchIndex entity) {
    if (entity == null) {
      return null;
    }
    return SearchIndexVO.builder()
        .id(entity.getId())
        .fileNodeId(entity.getFileNodeId())
        .name(entity.getName())
        .path(entity.getPath())
        .content(entity.getContent())
        .suffix(entity.getSuffix())
        .mimeType(entity.getMimeType())
        .size(entity.getSize())
        .tags(entity.getTags())
        .createdBy(entity.getCreatedBy())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }

  /**
   * 将 {@link SearchIndex} 实体列表批量转换为 {@link SearchIndexVO} 列表。
   *
   * @param entities 搜索索引实体列表
   * @return 搜索索引 VO 列表；入参为 {@code null} 或空时返回空列表
   */
  public List<SearchIndexVO> searchIndexListToVO(List<SearchIndex> entities) {
    if (entities == null || entities.isEmpty()) {
      return List.of();
    }
    return entities.stream().map(this::entityToVO).collect(Collectors.toList());
  }

  // ==================== ShareAccessLog 转换 ====================

  /**
   * 将 {@link ShareAccessLogDTO} 转换为 {@link ShareAccessLog} 实体。
   *
   * @param dto 分享访问日志 DTO，为 {@code null} 时返回 {@code null}
   * @return 分享访问日志实体，或 {@code null}
   */
  public ShareAccessLog dtoToEntity(ShareAccessLogDTO dto) {
    if (dto == null) {
      return null;
    }
    return ShareAccessLog.builder()
        .id(dto.getId())
        .shareId(dto.getShareId())
        .shareCode(dto.getShareCode())
        .fileNodeId(dto.getFileNodeId())
        .visitorId(dto.getVisitorId())
        .visitorName(dto.getVisitorName())
        .visitorIp(dto.getVisitorIp())
        .userAgent(dto.getUserAgent())
        .accessType(dto.getAccessType())
        .accessStatus(dto.getAccessStatus())
        .failReason(dto.getFailReason())
        .accessTime(dto.getAccessTime())
        .createdBy(dto.getCreatedBy())
        .build();
  }

  /**
   * 将 {@link ShareAccessLog} 实体转换为 {@link ShareAccessLogVO}。
   *
   * @param entity 分享访问日志实体，为 {@code null} 时返回 {@code null}
   * @return 分享访问日志 VO，或 {@code null}
   */
  public ShareAccessLogVO entityToVO(ShareAccessLog entity) {
    if (entity == null) {
      return null;
    }
    return ShareAccessLogVO.builder()
        .id(entity.getId())
        .shareId(entity.getShareId())
        .shareCode(entity.getShareCode())
        .fileNodeId(entity.getFileNodeId())
        .visitorId(entity.getVisitorId())
        .visitorName(entity.getVisitorName())
        .visitorIp(entity.getVisitorIp())
        .userAgent(entity.getUserAgent())
        .accessType(entity.getAccessType())
        .accessStatus(entity.getAccessStatus())
        .failReason(entity.getFailReason())
        .accessTime(entity.getAccessTime())
        .createdAt(entity.getCreatedAt())
        .updatedBy(entity.getUpdatedBy())
        .updatedAt(entity.getUpdatedAt())
        .revision(entity.getRevision())
        .deleted(entity.getDeleted())
        .build();
  }

  /**
   * 将 {@link ShareAccessLog} 实体列表批量转换为 {@link ShareAccessLogVO} 列表。
   *
   * @param entities 分享访问日志实体列表
   * @return 分享访问日志 VO 列表；入参为 {@code null} 或空时返回空列表
   */
  public List<ShareAccessLogVO> shareAccessLogListToVO(List<ShareAccessLog> entities) {
    if (entities == null || entities.isEmpty()) {
      return List.of();
    }
    return entities.stream().map(this::entityToVO).collect(Collectors.toList());
  }

  // ==================== ShareLink 转换（补充） ====================

  /**
   * 将 {@link ShareLinkDTO} 转换为 {@link ShareLink} 实体。
   *
   * @param dto 分享链接 DTO，为 {@code null} 时返回 {@code null}
   * @return 分享链接实体，或 {@code null}
   */
  public ShareLink dtoToEntity(ShareLinkDTO dto) {
    if (dto == null) {
      return null;
    }
    return ShareLink.builder()
        .fileNodeId(dto.getFileNodeId())
        .shareCode(dto.getShareCode())
        .extractCode(dto.getExtractCode())
        .shareType(dto.getShareType())
        .expireTime(dto.getExpireTime())
        .maxAccessCount(dto.getMaxAccessCount())
        .accessCount(dto.getAccessCount())
        .status(dto.getStatus())
        .password(dto.getPassword())
        .shareTargetType(dto.getShareTargetType())
        .title(dto.getTitle())
        .reminderSent(dto.getReminderSent())
        .createdBy(dto.getCreatedBy())
        .build();
  }

  /**
   * 将 {@link ShareLink} 实体转换为 {@link ShareLinkVO}。
   *
   * @param entity 分享链接实体，为 {@code null} 时返回 {@code null}
   * @return 分享链接 VO，或 {@code null}
   */
  public ShareLinkVO entityToVO(ShareLink entity) {
    if (entity == null) {
      return null;
    }
    return ShareLinkVO.builder()
        .id(entity.getId())
        .fileNodeId(entity.getFileNodeId())
        .shareCode(entity.getShareCode())
        .extractCode(entity.getExtractCode())
        .shareType(entity.getShareType())
        .expireTime(entity.getExpireTime())
        .maxAccessCount(entity.getMaxAccessCount())
        .accessCount(entity.getAccessCount())
        .status(entity.getStatus())
        .shareTargetType(entity.getShareTargetType())
        .password(entity.getPassword())
        .reminderSent(entity.getReminderSent())
        .title(entity.getTitle())
        .createdBy(entity.getCreatedBy())
        .updatedBy(entity.getUpdatedBy())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }

  /**
   * 将 {@link ShareLinkDTO} 转换为保留 ID 的 {@link ShareLink} 实体（用于更新场景）。
   *
   * @param dto 分享链接 DTO，为 {@code null} 时返回 {@code null}
   * @return 保留 ID 的分享链接实体，或 {@code null}
   */
  public ShareLink dtoToEntityWithId(ShareLinkDTO dto) {
    if (dto == null) {
      return null;
    }
    return ShareLink.builder()
        .id(dto.getId())
        .fileNodeId(dto.getFileNodeId())
        .shareCode(dto.getShareCode())
        .extractCode(dto.getExtractCode())
        .shareType(dto.getShareType())
        .expireTime(dto.getExpireTime())
        .maxAccessCount(dto.getMaxAccessCount())
        .accessCount(dto.getAccessCount())
        .status(dto.getStatus())
        .password(dto.getPassword())
        .shareTargetType(dto.getShareTargetType())
        .title(dto.getTitle())
        .reminderSent(dto.getReminderSent())
        .createdBy(dto.getCreatedBy())
        .build();
  }

  /**
   * 将 {@link ShareLink} 实体列表批量转换为 {@link ShareLinkVO} 列表。
   *
   * @param entities 分享链接实体列表
   * @return 分享链接 VO 列表；入参为 {@code null} 或空时返回空列表
   */
  public List<ShareLinkVO> shareLinkListToVO(List<ShareLink> entities) {
    if (entities == null || entities.isEmpty()) {
      return List.of();
    }
    return entities.stream().map(this::entityToVO).collect(Collectors.toList());
  }

  // ==================== StorageQuota 转换 ====================

  /**
   * 将 {@link StorageQuotaDTO} 转换为 {@link StorageQuota} 实体。
   *
   * @param dto 存储配额 DTO，为 {@code null} 时返回 {@code null}
   * @return 存储配额实体，或 {@code null}
   */
  public StorageQuota dtoToEntity(StorageQuotaDTO dto) {
    if (dto == null) {
      return null;
    }
    return StorageQuota.builder()
        .scopeType(dto.getScopeType())
        .scopeId(dto.getScopeId())
        .quotaLimit(dto.getQuotaLimit())
        .quotaUsed(dto.getQuotaUsed())
        .fileCountLimit(dto.getFileCountLimit())
        .fileCountUsed(dto.getFileCountUsed())
        .createdBy(dto.getCreatedBy())
        .updatedBy(dto.getUpdatedBy())
        .build();
  }

  /**
   * 将 {@link StorageQuota} 实体转换为 {@link StorageQuotaVO}。
   *
   * @param entity 存储配额实体，为 {@code null} 时返回 {@code null}
   * @return 存储配额 VO，或 {@code null}
   */
  public StorageQuotaVO entityToVO(StorageQuota entity) {
    if (entity == null) {
      return null;
    }
    return StorageQuotaVO.builder()
        .id(entity.getId())
        .scopeType(entity.getScopeType())
        .scopeId(entity.getScopeId())
        .quotaLimit(entity.getQuotaLimit())
        .quotaUsed(entity.getQuotaUsed())
        .fileCountLimit(entity.getFileCountLimit())
        .fileCountUsed(entity.getFileCountUsed())
        .createdBy(entity.getCreatedBy())
        .createdAt(entity.getCreatedAt())
        .updatedBy(entity.getUpdatedBy())
        .updatedAt(entity.getUpdatedAt())
        .revision(entity.getRevision())
        .deleted(entity.getDeleted())
        .build();
  }

  // ==================== Tag 转换 ====================

  /**
   * 将 {@link TagDTO} 转换为 {@link Tag} 实体。
   *
   * @param dto 标签 DTO，为 {@code null} 时返回 {@code null}
   * @return 标签实体，或 {@code null}
   */
  public Tag dtoToEntity(TagDTO dto) {
    if (dto == null) {
      return null;
    }
    return Tag.builder()
        .name(dto.getName())
        .color(dto.getColor())
        .type(dto.getType())
        .usageCount(dto.getUsageCount())
        .createdBy(dto.getCreatedBy())
        .updatedBy(dto.getUpdatedBy())
        .build();
  }

  /**
   * 将 {@link Tag} 实体转换为 {@link TagVO}。
   *
   * @param entity 标签实体，为 {@code null} 时返回 {@code null}
   * @return 标签 VO，或 {@code null}
   */
  public TagVO entityToVO(Tag entity) {
    if (entity == null) {
      return null;
    }
    return TagVO.builder()
        .id(entity.getId())
        .name(entity.getName())
        .color(entity.getColor())
        .type(entity.getType())
        .usageCount(entity.getUsageCount())
        .createdBy(entity.getCreatedBy())
        .createdAt(entity.getCreatedAt())
        .build();
  }

  /**
   * 将 {@link Tag} 实体列表批量转换为 {@link TagVO} 列表。
   *
   * @param entities 标签实体列表
   * @return 标签 VO 列表；入参为 {@code null} 或空时返回空列表
   */
  public List<TagVO> tagListToVO(List<Tag> entities) {
    if (entities == null || entities.isEmpty()) {
      return List.of();
    }
    return entities.stream().map(this::entityToVO).collect(Collectors.toList());
  }

  // ==================== TrashItem 转换（补充） ====================

  /**
   * 将 {@link TrashItemDTO} 转换为 {@link TrashItem} 实体。
   *
   * @param dto 回收站条目 DTO，为 {@code null} 时返回 {@code null}
   * @return 回收站条目实体，或 {@code null}
   */
  public TrashItem dtoToEntity(TrashItemDTO dto) {
    if (dto == null) {
      return null;
    }
    return TrashItem.builder()
        .fileNodeId(dto.getFileNodeId())
        .originalName(dto.getOriginalName())
        .originalPath(dto.getOriginalPath())
        .originalParentId(dto.getOriginalParentId())
        .nodeType(dto.getNodeType())
        .size(dto.getSize())
        .deletedTime(dto.getDeletedTime())
        .purgeTime(dto.getPurgeTime())
        .status(dto.getStatus())
        .createdBy(dto.getCreatedBy())
        .build();
  }

  /**
   * 将 {@link TrashItem} 实体转换为 {@link TrashItemVO}。
   *
   * @param entity 回收站条目实体，为 {@code null} 时返回 {@code null}
   * @return 回收站条目 VO，或 {@code null}
   */
  public TrashItemVO entityToVO(TrashItem entity) {
    if (entity == null) {
      return null;
    }
    return TrashItemVO.builder()
        .id(entity.getId())
        .fileNodeId(entity.getFileNodeId())
        .originalName(entity.getOriginalName())
        .originalPath(entity.getOriginalPath())
        .originalParentId(entity.getOriginalParentId())
        .nodeType(entity.getNodeType())
        .size(entity.getSize())
        .deletedTime(entity.getDeletedTime())
        .purgeTime(entity.getPurgeTime())
        .status(entity.getStatus())
        .createdBy(entity.getCreatedBy())
        .createdAt(entity.getCreatedAt())
        .build();
  }

  /**
   * 将 {@link TrashItemDTO} 转换为保留 ID 的 {@link TrashItem} 实体（用于更新场景）。
   *
   * @param dto 回收站条目 DTO，为 {@code null} 时返回 {@code null}
   * @return 保留 ID 的回收站条目实体，或 {@code null}
   */
  public TrashItem dtoToEntityWithId(TrashItemDTO dto) {
    if (dto == null) {
      return null;
    }
    return TrashItem.builder()
        .id(dto.getId())
        .fileNodeId(dto.getFileNodeId())
        .originalName(dto.getOriginalName())
        .originalPath(dto.getOriginalPath())
        .originalParentId(dto.getOriginalParentId())
        .nodeType(dto.getNodeType())
        .size(dto.getSize())
        .deletedTime(dto.getDeletedTime())
        .purgeTime(dto.getPurgeTime())
        .status(dto.getStatus())
        .createdBy(dto.getCreatedBy())
        .build();
  }

  /**
   * 将 {@link TrashItem} 实体列表批量转换为 {@link TrashItemVO} 列表。
   *
   * @param entities 回收站条目实体列表
   * @return 回收站条目 VO 列表；入参为 {@code null} 或空时返回空列表
   */
  public List<TrashItemVO> trashItemListToVO(List<TrashItem> entities) {
    if (entities == null || entities.isEmpty()) {
      return List.of();
    }
    return entities.stream().map(this::entityToVO).collect(Collectors.toList());
  }

  /**
   * 将 {@link TrashItemDTO} 列表批量转换为 {@link TrashItem} 实体列表。
   *
   * @param dtos 回收站条目 DTO 列表
   * @return 回收站条目实体列表；入参为 {@code null} 或空时返回空列表
   */
  public List<TrashItem> trashItemDtosToEntities(List<TrashItemDTO> dtos) {
    if (dtos == null || dtos.isEmpty()) {
      return List.of();
    }
    return dtos.stream().map(this::dtoToEntity).collect(Collectors.toList());
  }

  // ==================== FileTag 转换 ====================

  /**
   * 将 {@link FileTag} 实体转换为 {@link FileTagVO}。
   *
   * @param entity 文件-标签关联实体，为 {@code null} 时返回 {@code null}
   * @return 文件-标签关联 VO，或 {@code null}
   */
  public FileTagVO entityToVO(FileTag entity) {
    if (entity == null) {
      return null;
    }
    return FileTagVO.builder()
        .id(entity.getId())
        .fileNodeId(entity.getFileNodeId())
        .tagId(entity.getTagId())
        .createdBy(entity.getCreatedBy())
        .createdAt(entity.getCreatedAt())
        .build();
  }

  /**
   * 将 {@link FileTag} 实体列表批量转换为 {@link FileTagVO} 列表。
   *
   * @param entities 文件-标签关联实体列表
   * @return 文件-标签关联 VO 列表；入参为 {@code null} 或空时返回空列表
   */
  public List<FileTagVO> fileTagListToVO(List<FileTag> entities) {
    if (entities == null || entities.isEmpty()) {
      return List.of();
    }
    return entities.stream().map(this::entityToVO).collect(Collectors.toList());
  }

  // ==================== ShareRecipient 转换 ====================

  /**
   * 将 {@link ShareRecipientDTO} 转换为 {@link ShareRecipient} 实体。
   *
   * @param dto 分享目标用户 DTO，为 {@code null} 时返回 {@code null}
   * @return 分享目标用户实体，或 {@code null}
   */
  public ShareRecipient dtoToEntity(ShareRecipientDTO dto) {
    if (dto == null) {
      return null;
    }
    return ShareRecipient.builder()
        .shareId(dto.getShareId())
        .recipientType(dto.getRecipientType())
        .recipientId(dto.getRecipientId())
        .recipientName(dto.getRecipientName())
        .status(dto.getStatus())
        .viewedAt(dto.getViewedAt())
        .createdBy(dto.getCreatedBy())
        .updatedBy(dto.getUpdatedBy())
        .build();
  }

  /**
   * 将 {@link ShareRecipient} 实体转换为 {@link ShareRecipientVO}。
   *
   * @param entity 分享目标用户实体，为 {@code null} 时返回 {@code null}
   * @return 分享目标用户 VO，或 {@code null}
   */
  public ShareRecipientVO entityToVO(ShareRecipient entity) {
    if (entity == null) {
      return null;
    }
    return ShareRecipientVO.builder()
        .id(entity.getId())
        .shareId(entity.getShareId())
        .recipientType(entity.getRecipientType())
        .recipientId(entity.getRecipientId())
        .recipientName(entity.getRecipientName())
        .status(entity.getStatus())
        .viewedAt(entity.getViewedAt())
        .createdBy(entity.getCreatedBy())
        .createdAt(entity.getCreatedAt())
        .build();
  }

  /**
   * 将 {@link ShareRecipient} 实体列表批量转换为 {@link ShareRecipientVO} 列表。
   *
   * @param entities 分享目标用户实体列表
   * @return 分享目标用户 VO 列表；入参为 {@code null} 或空时返回空列表
   */
  public List<ShareRecipientVO> shareRecipientListToVO(List<ShareRecipient> entities) {
    if (entities == null || entities.isEmpty()) {
      return List.of();
    }
    return entities.stream().map(this::entityToVO).collect(Collectors.toList());
  }

  /**
   * 将 {@link ShareRecipientDTO} 列表批量转换为 {@link ShareRecipient} 实体列表。
   *
   * @param dtos 分享目标用户 DTO 列表
   * @return 分享目标用户实体列表；入参为 {@code null} 或空时返回空列表
   */
  public List<ShareRecipient> shareRecipientDtosToEntities(List<ShareRecipientDTO> dtos) {
    if (dtos == null || dtos.isEmpty()) {
      return List.of();
    }
    return dtos.stream().map(this::dtoToEntity).collect(Collectors.toList());
  }
}
