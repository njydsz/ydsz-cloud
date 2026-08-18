package com.njydsz.nextwiki.server.converter;

import java.util.List;

import com.njydsz.nextwiki.domain.dto.FileNodeDTO;
import com.njydsz.nextwiki.domain.vo.FileNodeVO;

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
}
