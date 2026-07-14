package com.njydsz.pmis.common.search.controller;

import java.util.List;
import java.util.Map;

import org.apache.skywalking.apm.toolkit.trace.Trace;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.pmis.common.search.analytics.SearchAnalyticsService;
import com.njydsz.pmis.common.search.api.SearchRequest;
import com.njydsz.pmis.common.search.api.SearchResponse;
import com.njydsz.pmis.common.search.api.SearchSuggestion;
import com.njydsz.pmis.common.search.service.IndexRebuildService;
import com.njydsz.pmis.common.search.service.SuggestionService;
import com.njydsz.pmis.common.search.service.UnifiedSearchService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 统一搜索 REST 控制器
 * <p>
 * 提供搜索、搜索建议、搜索分析和索引管理的 RESTful API。
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Slf4j
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@ConditionalOnClass(RestController.class)
@ConditionalOnProperty(prefix = "ydsz.search", name = "enabled", havingValue = "true", matchIfMissing = true)
@Tag(name = "统一搜索", description = "提供跨实体统一搜索、搜索建议、索引管理等功能")
public class SearchController {

    private final UnifiedSearchService searchService;
    private final SuggestionService suggestionService;
    private final SearchAnalyticsService analyticsService;
    private final IndexRebuildService indexRebuildService;

    /**
     * 统一搜索
     */
    @PostMapping("/query")
    @Operation(summary = "统一搜索", description = "支持跨实体类型搜索、高亮、模糊匹配、聚合分面")
    @Trace(operationName = "search_query")
    public SearchResponse search(@RequestBody SearchRequest request) {
        return searchService.search(request);
    }

    /**
     * 快速搜索（GET 方式）
     */
    @GetMapping
    @Operation(summary = "快速搜索", description = "GET 方式的简易搜索接口")
    @Trace(operationName = "search_quick")
    public SearchResponse quickSearch(
            @RequestParam String keyword,
            @RequestParam(required = false) List<String> types,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "true") boolean highlight,
            @RequestParam(defaultValue = "true") boolean fuzzy) {
        SearchRequest request = SearchRequest.builder()
                .keyword(keyword)
                .types(types)
                .page(page)
                .pageSize(pageSize)
                .highlight(highlight)
                .fuzzy(fuzzy)
                .build();
        return searchService.search(request);
    }

    /**
     * 搜索自动补全
     */
    @GetMapping("/suggest")
    @Operation(summary = "搜索自动补全", description = "根据用户输入前缀返回搜索建议")
    public List<String> suggest(@RequestParam String prefix) {
        return suggestionService.autocomplete(prefix);
    }

    /**
     * "您是不是要找"纠错建议
     */
    @GetMapping("/did-you-mean")
    @Operation(summary = "纠错建议", description = "零结果时返回拼写纠错建议")
    public List<String> didYouMean(@RequestParam String keyword) {
        return suggestionService.didYouMean(keyword);
    }

    /**
     * 热门搜索词
     */
    @GetMapping("/hot-keywords")
    @Operation(summary = "热门搜索词", description = "获取热门搜索关键词列表")
    public List<SearchAnalyticsService.HotKeyword> hotKeywords(
            @RequestParam(defaultValue = "10") int limit) {
        return analyticsService.getHotKeywords(limit);
    }

    /**
     * 零结果关键词
     */
    @GetMapping("/zero-result-keywords")
    @Operation(summary = "零结果关键词", description = "获取搜索无结果的关键词列表")
    public List<SearchAnalyticsService.HotKeyword> zeroResultKeywords(
            @RequestParam(defaultValue = "10") int limit) {
        return analyticsService.getZeroResultKeywords(limit);
    }

    /**
     * 搜索分析摘要
     */
    @GetMapping("/analytics/summary")
    @Operation(summary = "搜索分析摘要", description = "获取搜索统计摘要")
    public SearchAnalyticsService.SearchAnalyticsSummary analyticsSummary() {
        return analyticsService.getSummary();
    }

    /**
     * 每日搜索量统计
     */
    @GetMapping("/analytics/daily")
    @Operation(summary = "每日搜索量", description = "获取每日搜索量统计")
    public Map<java.time.LocalDate, Long> dailySearches(
            @RequestParam(defaultValue = "7") int days) {
        return analyticsService.getDailySearches(days);
    }

    /**
     * 清空搜索缓存
     */
    @DeleteMapping("/cache")
    @Operation(summary = "清空搜索缓存", description = "清除搜索结果缓存")
    public Map<String, String> clearCache() {
        searchService.clearCache();
        return Map.of("message", "搜索缓存已清空");
    }

    /**
     * 全量重建索引
     */
    @PostMapping("/index/rebuild")
    @Operation(summary = "全量重建索引", description = "触发索引全量重建（异步执行）")
    public Map<String, Object> rebuildIndex(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String tenantId) {
        if (indexRebuildService.isRebuilding()) {
            return Map.of("message", "重建任务正在执行中", "status", "running");
        }
        new Thread(() -> indexRebuildService.rebuildAll(type, tenantId), "index-rebuild").start();
        return Map.of("message", "索引重建已触发", "status", "started");
    }

    /**
     * 索引重建状态
     */
    @GetMapping("/index/rebuild/status")
    @Operation(summary = "索引重建状态", description = "获取索引重建进度")
    public Map<String, Object> rebuildStatus() {
        return Map.of(
                "rebuilding", indexRebuildService.isRebuilding(),
                "progress", indexRebuildService.getProgress(),
                "total", indexRebuildService.getTotal(),
                "registeredTypes", indexRebuildService.getRegisteredTypes()
        );
    }

    /**
     * 搜索建议（完整对象）
     */
    @GetMapping("/suggestion")
    @Operation(summary = "搜索建议", description = "返回完整的搜索建议对象")
    public SearchSuggestion suggestion(@RequestParam String prefix) {
        return searchService.suggest(prefix);
    }
}
