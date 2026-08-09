package com.njydsz.nextwiki.domain.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.common.domain.query.PageResponse;
import com.njydsz.nextwiki.domain.entity.FileNode;
import com.njydsz.nextwiki.domain.entity.SearchIndex;
import com.njydsz.nextwiki.domain.entity.Tag;
import com.njydsz.nextwiki.domain.repository.FileNodeRepository;
import com.njydsz.nextwiki.domain.repository.SearchIndexRepository;
import com.njydsz.nextwiki.domain.repository.TagRepository;
import com.njydsz.nextwiki.domain.vo.SearchResultVO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 搜索领域服务
 * <p>
 * 提供基于数据库的文件名、路径、标签搜索（多维度组合）。
 * 当 Elasticsearch 可用时，由 {@code WikiSearchProvider} 覆盖为全文搜索。
 *
 * <p><b>搜索能力分级：</b>
 * <ul>
 *   <li>P0 - 基于文件名/路径的 LIKE 搜索（数据库）</li>
 *   <li>P1 - 多维度搜索：文件名 + 路径 + 标签</li>
 *   <li>P2 - 相关度排序、高亮、搜索建议</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchDomainService {

    /** 分布式 ID 生成器 */
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    private final FileNodeRepository fileNodeRepository;
    private final TagRepository tagRepository;
    private final SearchIndexRepository searchIndexRepository;

    /**
     * 综合搜索（数据库分页，避免全量加载后内存分页）
     * <p>
     * 通过 nw_search_index 表的 LIMIT/OFFSET 在 SQL 层面分页，
     * 支持按 filename / content / tag / all 多维度搜索。
     *
     * @param keyword  搜索关键词
     * @param userId   用户ID（权限过滤）
     * @param scope    搜索范围：all / filename / content / tag
     * @param page     页码（从 1 开始）
     * @param pageSize 每页大小
     * @return 搜索结果
     */
    public SearchResultVO search(String keyword, String userId, String scope,
                                  int page, int pageSize) {
        long startTime = System.currentTimeMillis();

        log.info("[SearchDomainService] 搜索: keyword={}, userId={}, scope={}, page={}, pageSize={}",
                keyword, userId, scope, page, pageSize);

        // 数据库分页查询搜索索引，避免全量加载后内存 subList 分页
        PageResponse<SearchIndex> PageResponse = searchIndexRepository.searchPage(
                keyword, userId, scope, page, pageSize);

        List<SearchResultVO.SearchHitVO> hits = new ArrayList<>();
        for (SearchIndex index : PageResponse.getRecords()) {
            float score = calculateScore(index, keyword);
            hits.add(SearchResultVO.SearchHitVO.builder()
                    .fileNodeId(index.getFileNodeId())
                    .name(index.getName())
                    .path(index.getPath())
                    .nodeType(FileNode.TYPE_FILE)
                    .suffix(index.getSuffix())
                    .size(index.getSize())
                    .highlight(buildHighlight(index.getName(), keyword))
                    .score(score)
                    .tags(parseTags(index.getTags()))
                    .createdBy(index.getCreatedBy())
                    .updatedAt(index.getUpdatedAt() != null
                            ? index.getUpdatedAt().toString() : null)
                    .build());
        }

        long tookMs = System.currentTimeMillis() - startTime;

        return SearchResultVO.builder()
                .hits(hits)
                .total(PageResponse.getTotal())
                .page(page)
                .pageSize(pageSize)
                .tookMs(tookMs)
                .build();
    }

    /**
     * 索引同步（文件上传/更新后调用）
     * <p>
     * 将文件节点信息写入 nw_search_index 表，供数据库 fallback 搜索使用。
     *
     * @param fileNodeId 文件节点ID
     * @param content    提取的文本内容（可为 null）
     * @param userId     操作人ID
     */
    public void indexFile(String fileNodeId, String content, String userId) {
        log.info("[SearchDomainService] 索引文件: fileNodeId={}", fileNodeId);

        FileNode node = fileNodeRepository.findById(fileNodeId);
        if (node == null || node.getDeleted() != null && node.getDeleted() == 1) {
            log.warn("[SearchDomainService] 文件节点不存在或已删除，跳过索引: {}", fileNodeId);
            return;
        }

        // 查询标签
        List<Tag> tags = tagRepository.findByFileNodeId(fileNodeId);
        String tagNames = tags != null && !tags.isEmpty()
                ? tags.stream().map(Tag::getName).collect(Collectors.joining(","))
                : null;

        // 构建可搜索内容
        StringBuilder searchableContent = new StringBuilder();
        if (node.getName() != null) {
            searchableContent.append(node.getName());
        }
        if (node.getPath() != null) {
            searchableContent.append(' ').append(node.getPath());
        }
        if (content != null && !content.isEmpty()) {
            searchableContent.append(' ').append(content);
        }
        if (tagNames != null) {
            searchableContent.append(' ').append(tagNames);
        }

        SearchIndex index = SearchIndex.builder()
                .id(String.valueOf(snowflakeIdGenerator.nextId()).replace("-", ""))
                .fileNodeId(fileNodeId)
                .name(node.getName())
                .path(node.getPath())
                .content(searchableContent.toString())
                .suffix(node.getSuffix())
                .mimeType(node.getMimeType())
                .size(node.getSize())
                .tags(tagNames)
                .build();
        index.setCreatedBy(node.getCreatedBy());
        index.setUpdatedBy(userId);
        index.setRevision(0);
        index.setDeleted(0);

        searchIndexRepository.upsert(index);
        log.info("[SearchDomainService] 索引写入成功: fileNodeId={}", fileNodeId);
    }

    /**
     * 删除索引
     */
    public void removeIndex(String fileNodeId) {
        log.info("[SearchDomainService] 删除索引: fileNodeId={}", fileNodeId);
        searchIndexRepository.deleteByFileNodeId(fileNodeId);
    }

    /**
     * 重建全量索引
     * <p>
     * 查询所有未删除的文件节点，逐个写入索引。
     */
    public void rebuildAllIndices() {
        log.info("[SearchDomainService] 重建全量索引（异步任务）");

        List<String> fileNodeIds = searchIndexRepository.findAllFileNodeIds(null);
        log.info("[SearchDomainService] 待索引文件数: {}", fileNodeIds.size());

        int success = 0;
        int failed = 0;
        for (String fileNodeId : fileNodeIds) {
            try {
                indexFile(fileNodeId, null, null);
                success++;
            } catch (Exception e) {
                log.error("[SearchDomainService] 索引重建失败: fileNodeId={}", fileNodeId, e);
                failed++;
            }
        }

        log.info("[SearchDomainService] 全量索引重建完成: success={}, failed={}", success, failed);
    }

    // ==================== 私有方法 ====================

    /**
     * 计算搜索得分（0-1 之间，越高越相关）
     */
    private float calculateScore(SearchIndex index, String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return 1.0f;
        }
        String name = index.getName() != null ? index.getName().toLowerCase() : "";
        String lowerKeyword = keyword.toLowerCase();

        if (name.equals(lowerKeyword)) return 1.0f;
        if (name.startsWith(lowerKeyword)) return 0.8f;
        if (name.contains(lowerKeyword)) return 0.6f;

        String path = index.getPath() != null ? index.getPath().toLowerCase() : "";
        if (path.contains(lowerKeyword)) return 0.3f;

        return 0.1f;
    }

    /**
     * 构建高亮片段（基于文件名匹配关键词的位置）
     */
    private String buildHighlight(String name, String keyword) {
        if (keyword == null || keyword.isEmpty() || name == null) {
            return null;
        }
        if (name.toLowerCase().contains(keyword.toLowerCase())) {
            int idx = name.toLowerCase().indexOf(keyword.toLowerCase());
            int start = Math.max(0, idx - 20);
            int end = Math.min(name.length(), idx + keyword.length() + 20);
            String prefix = start > 0 ? "..." : "";
            String suffix = end < name.length() ? "..." : "";
            return prefix + name.substring(start, end) + suffix;
        }
        return null;
    }

    /**
     * 解析逗号分隔的标签字符串为列表
     */
    private List<String> parseTags(String tags) {
        if (tags == null || tags.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
