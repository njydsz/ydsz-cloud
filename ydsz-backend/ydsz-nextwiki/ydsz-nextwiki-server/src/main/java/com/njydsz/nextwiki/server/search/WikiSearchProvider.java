package com.njydsz.nextwiki.server.search;

import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Component;

import com.njydsz.common.search.api.SearchFilter;
import com.njydsz.common.search.core.IndexDocument;
import com.njydsz.common.search.core.SearchField;
import com.njydsz.common.search.core.SearchField.FieldType;
import com.njydsz.common.search.provider.SearchProvider;
import com.njydsz.common.search.provider.SearchProviderContext;
import com.njydsz.nextwiki.domain.entity.FileNode;
import com.njydsz.nextwiki.domain.entity.Tag;
import com.njydsz.nextwiki.domain.repository.FileNodeRepository;
import com.njydsz.nextwiki.domain.repository.SearchIndexRepository;
import com.njydsz.nextwiki.domain.repository.TagRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 知识库文件搜索提供者
 * <p>
 * 将 nextwiki 文件节点注册到统一搜索体系，支持文件名、路径、标签搜索。
 *
 * <h3>重构（1.3.0）</h3>
 * <ul>
 *   <li>使用新 {@link SearchField} API（FieldType + searchable + sortable + aggregatable）</li>
 *   <li>通过 {@link TagRepository} 加载文件标签，填充 {@link IndexDocument#getTags()}</li>
 *   <li>实现 {@link #getFilters(SearchProviderContext)} 权限过滤（非管理员仅搜到自己创建的文件）</li>
 *   <li>去除冗余的 searchableText 拼接 — 引擎策略自行组合 title + subtitle + content + tags</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.3.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WikiSearchProvider implements SearchProvider<FileNode> {

    private final FileNodeRepository fileNodeRepository;
    private final SearchIndexRepository searchIndexRepository;
    private final TagRepository tagRepository;

    @Override
    public String getType() {
        return "wiki";
    }

    @Override
    public String getTypeLabel() {
        return "知识库";
    }

    @Override
    public IndexDocument toIndexDocument(FileNode node) {
        if (node == null || node.getId() == null) {
            return null;
        }

        List<String> tagNames = List.of();
        try {
            List<Tag> tags = tagRepository.findByFileNodeId(node.getId());
            if (tags != null && !tags.isEmpty()) {
                tagNames = tags.stream().map(Tag::getName).filter(n -> n != null && !n.isBlank()).toList();
            }
        } catch (Exception e) {
            log.debug("[WikiSearchProvider] 加载标签失败: nodeId={}", node.getId(), e);
        }

        return IndexDocument.builder()
                .id(node.getId())
                .type("wiki")
                .title(node.getName())
                .subtitle(node.getPath())
                .snippet(node.getPath())
                .tags(tagNames)
                .status(node.getShareStatus())
                .path("/nextwiki/files/" + node.getId())
                .tenantId(node.getTenantId())
                .createdBy(node.getCreatedBy())
                .createdAt(node.getCreatedAt() != null
                        ? node.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant() : null)
                .updatedBy(node.getUpdatedBy())
                .updatedAt(node.getUpdatedAt() != null
                        ? node.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant() : null)
                .build();
    }

    @Override
    public List<SearchField> getSearchableFields() {
        return List.of(
                SearchField.builder()
                        .name("title").label("文件名").type(FieldType.TEXT)
                        .weight(3.0f).searchable(true).highlightable(true).sortable(true)
                        .build(),
                SearchField.builder()
                        .name("subtitle").label("路径").type(FieldType.TEXT)
                        .weight(2.0f).searchable(true).highlightable(true)
                        .build(),
                SearchField.builder()
                        .name("content").label("全文").type(FieldType.TEXT)
                        .weight(1.0f).searchable(true).highlightable(true)
                        .build(),
                SearchField.builder()
                        .name("tags").label("标签").type(FieldType.TAG)
                        .weight(1.5f).searchable(true).aggregatable(true)
                        .build(),
                SearchField.builder()
                        .name("status").label("共享状态").type(FieldType.KEYWORD)
                        .weight(0.5f).searchable(false).aggregatable(true)
                        .build(),
                SearchField.builder()
                        .name("suffix").label("文件类型").type(FieldType.KEYWORD)
                        .weight(0.5f).searchable(false).aggregatable(true)
                        .build()
        );
    }

    @Override
    public List<SearchFilter> getFilters(SearchProviderContext context) {
        if (context == null || context.isAdmin()) {
            return List.of();
        }
        if (context.getUserId() == null || context.getUserId().isBlank()) {
            return List.of();
        }
        return List.of(SearchFilter.builder()
                .field("created_by")
                .values(List.of(context.getUserId()))
                .operator(SearchFilter.Operator.EQ)
                .build());
    }

    @Override
    public List<String> getAllDocumentIds(String tenantId) {
        log.info("[WikiSearchProvider] 获取全部文件 ID: tenantId={}", tenantId);
        // SearchIndexRepository.findAllFileNodeIds 参数为 createdBy，传 null 查询全部
        return searchIndexRepository.findAllFileNodeIds(null);
    }

    @Override
    public FileNode loadById(String id) {
        return fileNodeRepository.findById(id);
    }
}
