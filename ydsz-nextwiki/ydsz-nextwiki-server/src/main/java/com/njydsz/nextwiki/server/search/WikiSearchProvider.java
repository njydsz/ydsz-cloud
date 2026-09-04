package com.njydsz.nextwiki.server.search;

import java.time.ZoneId;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.common.search.api.SearchFilter;
import com.njydsz.common.search.core.IndexDocument;
import com.njydsz.common.search.core.SearchField.FieldType;
import com.njydsz.common.search.core.SearchField;
import com.njydsz.common.search.provider.SearchProvider;
import com.njydsz.common.search.provider.SearchProviderContext;
import com.njydsz.common.util.message.MessageUtils;
import com.njydsz.nextwiki.domain.repository.FileNodeRepository;
import com.njydsz.nextwiki.domain.repository.SearchIndexRepository;
import com.njydsz.nextwiki.domain.repository.TagRepository;
import com.njydsz.nextwiki.domain.vo.FileNodeVO;
import com.njydsz.nextwiki.domain.vo.TagVO;

/**
 * 知识库文件搜索提供者
 *
 * <p>将 nextwiki 文件节点注册到统一搜索体系，支持文件名、路径、标签搜索。
 *
 * <h3>重构（1.3.0）</h3>
 *
 * <ul>
 *   <li>使用新 {@link SearchField} API（FieldType + searchable + sortable + aggregatable）
 *   <li>通过 {@link TagRepository} 加载文件标签，填充 {@link IndexDocument#getTags()}
 *   <li>实现 {@link #getFilters(SearchProviderContext)} 权限过滤（非管理员仅搜到自己创建的文件）
 *   <li>去除冗余的 searchableText 拼接 — 引擎策略自行组合 title + subtitle + content + tags
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WikiSearchProvider implements SearchProvider<FileNodeVO> {

  /** 字段权重：标题（最高） */
  private static final float WEIGHT_TITLE = 3.0f;

  /** 字段权重：内容 */
  private static final float WEIGHT_CONTENT = 2.0f;

  /** 字段权重：作者/备注 */
  private static final float WEIGHT_AUTHOR = 1.0f;

  /** 字段权重：标签 */
  private static final float WEIGHT_TAGS = 1.5f;

  /** 字段权重：状态/类型等低权重字段 */
  private static final float WEIGHT_LOW = 0.5f;


  private final FileNodeRepository fileNodeRepository;
  private final SearchIndexRepository searchIndexRepository;
  private final TagRepository tagRepository;

  @Override
  public String getType() {
    return "wiki";
  }

  /**
   * 获取类型标签（当前 SearchProvider 接口未定义该方法，保留为类自有能力）。
   *
   * @return 类型标签
   */
  public String getTypeLabel() {
    return MessageUtils.getMessage("nextwiki.search.typeLabel", "知识库");
  }

  @Override
  public IndexDocument toIndexDocument(FileNodeVO node) {
    if (node == null || node.getId() == null) {
      return null;
    }

    List<String> tagNames = List.of();
    try {
      List<TagVO> tags = tagRepository.findByFileNodeId(node.getId());
      if (tags != null && !tags.isEmpty()) {
        tagNames = tags.stream().map(TagVO::getName).filter(n -> n != null && !n.isBlank()).toList();
      }
    } catch (Exception e) {
      log.debug("[WikiSearchProvider] 加载标签失败: nodeId={}", node.getId(), e);
    }

    // 全文内容（P1-5 修复）：文档正文由 ContentExtractionApplicationService 解析后，
    // 通过 SearchDomainService.indexFile(fileNodeId, content, userId) 直接写入索引，
    // 不再经 FileNodeVO 内存字段传递（移除原 searchableContent 死字段）。
    // 此处 content 置空，仅索引元数据（文件名/路径/标签）。
    String content = null;

    return IndexDocument.builder()
        .id(node.getId())
        .type("wiki")
        .title(node.getName())
        .subtitle(node.getPath())
        .content(content)
        .snippet(node.getPath())
        .tags(tagNames)
        .status(node.getShareStatus())
        .path("/nextwiki/files/" + node.getId())
        .tenantId(node.getTenantId())
        .createdBy(node.getCreatedBy())
        .createdAt(
            node.getCreatedAt() != null
                ? node.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant()
                : null)
        .updatedBy(node.getUpdatedBy())
        .updatedAt(
            node.getUpdatedAt() != null
                ? node.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant()
                : null)
        .build();
  }

  /**
   * 获取可搜索字段定义（供前端筛选/高亮能力声明；当前 SearchProvider 接口未定义该方法，保留为类自有能力）。
   *
   * @return 可搜索字段列表
   */
  public List<SearchField> getSearchableFields() {
    return List.of(
        SearchField.builder()
            .name("title")
            .label(MessageUtils.getMessage("nextwiki.search.field.name", "文件名"))
            .type(FieldType.TEXT)
            .weight(WEIGHT_TITLE)
            .searchable(true)
            .highlightable(true)
            .sortable(true)
            .build(),
        SearchField.builder()
            .name("subtitle")
            .label(MessageUtils.getMessage("nextwiki.search.field.path", "路径"))
            .type(FieldType.TEXT)
            .weight(WEIGHT_CONTENT)
            .searchable(true)
            .highlightable(true)
            .build(),
        SearchField.builder()
            .name("content")
            .label(MessageUtils.getMessage("nextwiki.search.field.content", "全文"))
            .type(FieldType.TEXT)
            .weight(WEIGHT_AUTHOR)
            .searchable(true)
            .highlightable(true)
            .build(),
        SearchField.builder()
            .name("tags")
            .label(MessageUtils.getMessage("nextwiki.search.field.tags", "标签"))
            .type(FieldType.TAG)
            .weight(WEIGHT_TAGS)
            .searchable(true)
            .aggregatable(true)
            .build(),
        SearchField.builder()
            .name("status")
            .label(MessageUtils.getMessage("nextwiki.search.field.shareStatus", "共享状态"))
            .type(FieldType.KEYWORD)
            .weight(WEIGHT_LOW)
            .searchable(false)
            .aggregatable(true)
            .build(),
        SearchField.builder()
            .name("suffix")
            .label(MessageUtils.getMessage("nextwiki.search.field.fileType", "文件类型"))
            .type(FieldType.KEYWORD)
            .weight(WEIGHT_LOW)
            .searchable(false)
            .aggregatable(true)
            .build());
  }

  @Override
  public List<SearchFilter> getFilters(SearchProviderContext context) {
    if (context == null || context.isAdmin()) {
      return List.of();
    }
    if (context.getUserId() == null || context.getUserId().isBlank()) {
      return List.of();
    }
    return List.of(
        SearchFilter.builder()
            .field("created_by")
            .values(List.of(context.getUserId()))
            .operator(SearchFilter.Operator.EQ)
            .build());
  }

  /**
   * 获取全部文件 ID（供索引重建/同步引擎调用；当前 SearchProvider 接口未定义该方法，保留为类自有能力）。
   *
   * @param tenantId 租户 ID（未使用，保留签名兼容）
   * @return 全部文件节点 ID
   */
  public List<String> getAllDocumentIds(String tenantId) {
    log.info("[WikiSearchProvider] 获取全部文件 ID: tenantId={}", tenantId);
    // SearchIndexRepository.findAllFileNodeIds 参数为 createdBy，传 null 查询全部
    return searchIndexRepository.findAllFileNodeIds(null);
  }

  /**
   * 按 ID 加载文件节点（供索引重建/同步引擎调用；当前 SearchProvider 接口未定义该方法，保留为类自有能力）。
   *
   * @param id 文件节点 ID
   * @return 文件节点 VO；不存在返回 {@code null}
   */
  public FileNodeVO loadById(String id) {
    return fileNodeRepository.findById(id).orElse(null);
  }
}
