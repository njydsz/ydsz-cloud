package com.njydsz.nextwiki.server.converter;

import java.util.List;

import com.njydsz.nextwiki.domain.dto.FileNodeDTO;
import com.njydsz.nextwiki.domain.dto.FileVersionDTO;
import com.njydsz.nextwiki.domain.vo.FileNodeVO;
import com.njydsz.nextwiki.domain.vo.FileVersionVO;

/**
 * NextWiki 实体 ↔ VO 转换器。
 *
 * <p>采用单例 + 手动映射方式，避免引入 MapStruct 编译期依赖。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
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
        .fileHash(vo.getFileHash())
        .path(vo.getPath())
        .level(vo.getLevel())
        .sort(vo.getSort())
        .currentVersion(vo.getCurrentVersion())
        .thumbnailKey(vo.getThumbnailKey())
        .previewReady(vo.getPreviewReady())
        .starred(vo.getStarred())
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
        .fileHash(dto.getFileHash())
        .path(dto.getPath())
        .level(dto.getLevel())
        .sort(dto.getSort())
        .currentVersion(dto.getCurrentVersion())
        .thumbnailKey(dto.getThumbnailKey())
        .previewReady(dto.getPreviewReady())
        .starred(dto.getStarred())
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
}
