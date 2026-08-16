package com.njydsz.nextwiki.server.converter;

import com.njydsz.nextwiki.domain.entity.FileNode;
import com.njydsz.nextwiki.domain.vo.FileNodeVO;
import java.util.Collections;
import java.util.List;

/**
 * NextWiki 实体 ↔ VO 转换器。
 *
 * <p>采用单例 + 手动映射方式，避免引入 MapStruct 编译期依赖。 当前仅提供 {@link FileNode} → {@link FileNodeVO} 的转换；
 * 后续如需新增其他实体转换，可在此类中扩展对应方法。
 *
 * <p><b>字段映射说明：</b>
 *
 * <ul>
 *   <li>{@code thumbnailKey}（存储键）→ {@code thumbnailUrl}：此处直接透传存储键， 由前端或 CDN 网关拼接完整可访问 URL。
 *   <li>{@code children} / {@code tags}：默认填充空集合（{@link Collections#emptyList()}），
 *       避免下游空指针；调用方如需注入实际数据，可通过 {@link FileNodeVO#setChildren(List)} / {@link
 *       FileNodeVO#setTags(List)} 覆盖。
 *   <li>审计字段（createdBy / createdAt / updatedAt）：从 {@code MpBaseEntity} 继承字段透传。
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class NextwikiConverter {

  /** 单例实例（保持与既有调用 {@code NextwikiConverter.INSTANT.entityToVO(...)} 兼容） */
  public static final NextwikiConverter INSTANT = new NextwikiConverter();

  private NextwikiConverter() {}

  /**
   * 将 {@link FileNode} 实体转换为 {@link FileNodeVO} 视图对象。
   *
   * <p>仅做扁平字段映射，不递归加载子节点与标签列表。
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
        .level(entity.getLevel())
        .sort(entity.getSort())
        .currentVersion(entity.getCurrentVersion())
        .starred(entity.getStarred())
        .shareStatus(entity.getShareStatus())
        .thumbnailUrl(entity.getThumbnailKey())
        .previewReady(entity.getPreviewReady())
        .createdBy(entity.getCreatedBy())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .children(Collections.emptyList())
        .tags(Collections.emptyList())
        .build();
  }
}
