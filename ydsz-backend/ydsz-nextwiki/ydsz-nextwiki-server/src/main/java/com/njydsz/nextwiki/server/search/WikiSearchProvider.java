package com.njydsz.nextwiki.server.search;

import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Component;

import com.njydsz.common.search.core.IndexDocument;
import com.njydsz.common.search.core.SearchField;
import com.njydsz.common.search.provider.SearchProvider;
import com.njydsz.nextwiki.domain.entity.FileNode;
import com.njydsz.nextwiki.domain.repository.FileNodeRepository;
import com.njydsz.nextwiki.domain.repository.SearchIndexRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 知识库文件搜索提供者
 * <p>
 * 将 nextwiki 文件节点注册到统一搜索体系，支持文件名、路径、标签搜索。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WikiSearchProvider implements SearchProvider<FileNode> {

    private final FileNodeRepository fileNodeRepository;
    private final SearchIndexRepository searchIndexRepository;

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

        // 构建可搜索文本
        StringBuilder searchableText = new StringBuilder();
        if (node.getName() != null) {
            searchableText.append(node.getName());
        }
        if (node.getPath() != null) {
            searchableText.append(' ').append(node.getPath());
        }

        return IndexDocument.builder()
                .id(node.getId())
                .type("wiki")
                .title(node.getName())
                .subtitle(node.getPath())
                .content(searchableText.toString())
                .snippet(node.getPath())
                .status(node.getShareStatus())
                .path("/nextwiki/files/" + node.getId())
                .tenantId(node.getCreatedBy())
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
                SearchField.builder().name("title").label("文件名").weight(3.0f).highlightable(true).build(),
                SearchField.builder().name("subtitle").label("路径").weight(2.0f).highlightable(true).build(),
                SearchField.builder().name("content").label("全文").weight(1.0f).highlightable(true).build(),
                SearchField.builder().name("status").label("共享状态").weight(1.0f).aggregatable(true).build()
        );
    }

    @Override
    public List<String> getAllDocumentIds(String tenantId) {
        // 返回所有未删除的文件节点 ID，供搜索框架全量索引重建使用
        log.info("[WikiSearchProvider] 获取全部文件 ID: tenantId={}", tenantId);
        return searchIndexRepository.findAllFileNodeIds(tenantId);
    }

    @Override
    public FileNode loadById(String id) {
        return fileNodeRepository.findById(id);
    }
}
