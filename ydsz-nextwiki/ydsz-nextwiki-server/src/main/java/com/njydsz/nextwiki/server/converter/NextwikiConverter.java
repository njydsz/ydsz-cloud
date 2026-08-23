package com.njydsz.nextwiki.server.converter;

import java.util.List;

import com.njydsz.nextwiki.domain.dto.FileNodeDTO;
import com.njydsz.nextwiki.domain.dto.FileVersionDTO;
import com.njydsz.nextwiki.domain.dto.ShareLinkDTO;
import com.njydsz.nextwiki.domain.dto.TrashItemDTO;
import com.njydsz.nextwiki.domain.vo.FileNodeVO;
import com.njydsz.nextwiki.domain.vo.FileVersionVO;
import com.njydsz.nextwiki.domain.vo.ShareLinkVO;
import com.njydsz.nextwiki.domain.vo.TrashItemVO;

/**
 * NextWiki 实体 ↔ VO 转换器。
 *
 * <p>采用单例 + 手动映射方式，避免引入 MapStruct 编译期依赖。
 *
 * @author ydsz-team
 * @since 1.0.0
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
    return vos.stream().map(this::versionToDTO).collect(java.util.stream.Collectors.toList());
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
    return dtos.stream().map(this::versionToVO).collect(java.util.stream.Collectors.toList());
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
    return vos.stream().map(this::shareLinkToDTO).collect(java.util.stream.Collectors.toList());
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
    return vos.stream().map(this::trashItemToDTO).collect(java.util.stream.Collectors.toList());
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
}
