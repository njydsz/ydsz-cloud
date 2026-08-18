package com.njydsz.nextwiki.domain.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.nextwiki.domain.vo.FileNodeVO;
import com.njydsz.nextwiki.domain.vo.SearchIndexVO;
import com.njydsz.nextwiki.domain.vo.TagVO;
import com.njydsz.nextwiki.domain.dto.SearchIndexDTO;
import com.njydsz.nextwiki.domain.vo.SearchResultVO;

/**
 * 搜索领域服务
 *
 * <p>维护 {@code nw_search_index} 表，提供基于数据库的文件名/路径/标签搜索能力。
 *
 * <p><b>职责定位（双索引架构）：</b>
 *
 * <ul>
 *   <li>{@code nw_search_index} 表 — 搜索引擎不可用时的 <b>DB 降级存储</b>， 仅在统一搜索（{@code
 *       ydsz-common-search}）不可用时提供 LIKE 兜底
 *   <li>{@code ydsz_search_index} 表（{@code WikiSearchProvider} 维护）— 统一搜索引擎的主索引，支持全文检索、高亮、聚合、权重排序
 * </ul>
 *
 * <p><b>搜索优先级：</b>
 *
 * <ol>
 *   <li>统一搜索引擎（PG tsvector / 内存引擎）— {@link com.njydsz.common.search.service.UnifiedSearchService}
 *   <li>DB LIKE 降级 — 本类提供（仅在引擎不可用时触发）
 * </ol>
 *
 * <p><b>分层原则：</b> 本类仅包含纯领域逻辑（搜索评分、高亮构建、索引组装）。 数据访问由 server 层负责，通过方法参数传入所需数据。
 *
 * <p><b>注意事项：</b> 增量索引写入由 {@code FileOperatedEventListener} 驱动，同时更新 nw_search_index 和统一搜索索引。 全量重建由
 * {@code NextwikiScheduledJobs} 触发，确保双索引数据一致性。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.nextwiki.server.search.WikiSearchProvider 统一搜索 Provider（主索引）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchDomainService {

  /** 文件类型常量 */
  private static final String TYPE_FILE = "file";

  /**
   * 同步到统一搜索索引的最大内容长度（字符数）。
   *
   * <p>防止过大的文档全文写入索引导致存储膨胀。 PG tsvector 索引对超长文本会自动截断，此处显式控制保证一致性。
   */
  private static final int MAX_SEARCHABLE_CONTENT_LENGTH = 100_000;

  /** 分布式 ID 生成器 */
  private final SnowflakeIdGenerator snowflakeIdGenerator;

  /**
   * 综合搜索（纯领域逻辑，数据由 server 层传入）
   *
   * <p>对已分页查询的搜索索引列表进行评分、高亮构建和结果组装。 分页查询由 server 层通过 {@code SearchIndexRepository} 完成，
   * 本类仅负责领域逻辑处理。
   *
   * @param indices 已分页的搜索索引列表（由 server 层查询传入）
   * @param total 总匹配数（由 server 层查询传入）
   * @param keyword 搜索关键词
   * @param page 页码（从 1 开始）
   * @param pageSize 每页大小
   * @return 搜索结果
   */
  public SearchResultVO search(
      List<SearchIndexVO> indices, long total, String keyword, int page, int pageSize) {
    long startTime = System.currentTimeMillis();

    log.info(
        "[SearchDomainService] 搜索: keyword={}, total={}, page={}, pageSize={}",
        keyword,
        total,
        page,
        pageSize);

    List<SearchResultVO.SearchHitVO> hits = new ArrayList<>();
    for (SearchIndexVO index : indices) {
      float score = calculateScore(index, keyword);
      hits.add(
          SearchResultVO.SearchHitVO.builder()
              .fileNodeId(index.getFileNodeId())
              .name(index.getName())
              .path(index.getPath())
              .nodeType(TYPE_FILE)
              .suffix(index.getSuffix())
              .size(index.getSize())
              .highlight(buildHighlight(index.getName(), keyword))
              .score(score)
              .tags(parseTags(index.getTags()))
              .createdBy(index.getCreatedBy())
              .updatedAt(index.getUpdatedAt() != null ? index.getUpdatedAt().toString() : null)
              .build());
    }

    long tookMs = System.currentTimeMillis() - startTime;

    return SearchResultVO.builder()
        .hits(hits)
        .total(total)
        .page(page)
        .pageSize(pageSize)
        .tookMs(tookMs)
        .build();
  }

  /**
   * 构建搜索索引 DTO（纯领域逻辑，数据由 server 层传入）
   *
   * <p>根据文件节点和标签数据，构建 {@link SearchIndexDTO} DTO。 DTO 持久化由 server 层通过 {@code SearchIndexRepository} 完成。
   *
   * @param node 文件节点 VO（由 server 层查询传入，须保证非 null 且未删除）
   * @param tags 文件关联的标签列表（由 server 层查询传入，可为 null 或空）
   * @param content 提取的文本内容（可为 null）
   * @param userId 操作人ID
   * @return 构建完成的搜索索引 DTO
   */
  public SearchIndexDTO buildSearchIndex(
      FileNodeVO node, List<TagVO> tags, String content, String userId) {
    log.info("[SearchDomainService] 构建搜索索引: fileNodeId={}", node.getId());

    String tagNames =
        tags != null && !tags.isEmpty()
            ? tags.stream().map(TagVO::getName).collect(Collectors.joining(","))
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

    SearchIndexDTO dto =
        SearchIndexDTO.builder()
            .id(String.valueOf(snowflakeIdGenerator.nextId()).replace("-", ""))
            .fileNodeId(node.getId())
            .name(node.getName())
            .path(node.getPath())
            .content(searchableContent.toString())
            .suffix(node.getSuffix())
            .mimeType(node.getMimeType())
            .size(node.getSize())
            .tags(tagNames)
            .build();

    log.info("[SearchDomainService] 搜索索引构建完成: fileNodeId={}", node.getId());
    return dto;
  }

  // ==================== 私有方法 ====================

  /** 计算搜索得分（0-1 之间，越高越相关） */
  private float calculateScore(SearchIndexVO index, String keyword) {
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

  /** 构建高亮片段（基于文件名匹配关键词的位置） */
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

  /** 解析逗号分隔的标签字符串为列表 */
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
