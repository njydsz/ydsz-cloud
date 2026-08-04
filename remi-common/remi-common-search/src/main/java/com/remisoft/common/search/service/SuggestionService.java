package com.remisoft.common.search.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.remisoft.common.search.api.SearchSuggestion;
import com.remisoft.common.search.config.SearchProperties;
import com.remisoft.common.search.core.SearchEngineRegistry;
import com.remisoft.common.search.core.SuggestStrategy;

import lombok.extern.slf4j.Slf4j;

/**
 * 搜索建议服务接口。
 * <p>输入前缀返回候选词/热门词。
 *
 * @author remi-team
 * @since 1.0.0
 */


@Slf4j
public class SuggestionService {

    private final SearchEngineRegistry engineRegistry;
    private final SearchProperties properties;

    public SuggestionService(SearchEngineRegistry engineRegistry, SearchProperties properties) {
        this.engineRegistry = engineRegistry;
        this.properties = properties;
    }

    /**
     * 根据输入前缀返回自动补全候选词。
     *
     * <p>召回策略分两轮：先向底层 {@link SuggestStrategy} 多取一倍候选
     * （{@code suggestLimit * 2}），第一轮只保留前缀匹配或包含匹配的词；
     * 若数量不足 {@code suggestLimit}，第二轮用剩余候选去重补齐，
     * 以避免用户在输入过程中看到空下拉框。
     *
     * <p><b>降级策略</b>：未注册 {@link SuggestStrategy}、或底层查询抛出任何异常时，
     * 均返回空列表并记 warn 日志，不向上抛异常——补全属于体验增强功能，不应阻断搜索。
     *
     * @param prefix 用户已输入的前缀，为 {@code null} 或空白时直接返回空列表；内部按 trim + 小写归一化
     * @return 候选词列表，最多 {@code remi.search.suggest-limit} 条；无结果时返回空列表而非 {@code null}
     */
    public List<String> autocomplete(String prefix) {
        if (prefix == null || prefix.isBlank()) return Collections.emptyList();
        try {
            Optional<SuggestStrategy> suggestStrategy = engineRegistry.getSuggestStrategy();
            if (suggestStrategy.isEmpty()) return Collections.emptyList();
            String normalizedPrefix = prefix.trim().toLowerCase();
            SearchSuggestion suggestion = suggestStrategy.get().suggest(normalizedPrefix, properties.getSuggestLimit() * 2);
            List<String> results = new ArrayList<>();
            if (suggestion != null && suggestion.getSuggestions() != null) {
                for (String s : suggestion.getSuggestions()) {
                    if (s != null && !s.isBlank()) {
                        String lower = s.toLowerCase();
                        if (lower.startsWith(normalizedPrefix) || lower.contains(normalizedPrefix)) {
                            results.add(s);
                        }
                    }
                }
            }
            if (results.size() < properties.getSuggestLimit()) {
                if (suggestion != null && suggestion.getSuggestions() != null) {
                    for (String s : suggestion.getSuggestions()) {
                        if (s != null && !s.isBlank() && !results.contains(s)) {
                            results.add(s);
                        }
                        if (results.size() >= properties.getSuggestLimit()) break;
                    }
                }
            }
            return results.stream().limit(properties.getSuggestLimit()).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("[SuggestionService] 自动补全失败: prefix={}", prefix, e);
            return Collections.emptyList();
        }
    }

    /**
     * 为疑似拼写错误的关键词生成「您是不是要找」纠错建议。
     *
     * <p>算法：取关键词前 2 个字符作为宽松前缀向底层多召回一批候选
     * （{@code suggestLimit * 3}），再按与原词的 Levenshtein 编辑距离升序排序并过滤。
     * 编辑距离阈值随词长放宽：长度 ≤2 允许 1，≤5 允许 2，更长允许 3，
     * 兼顾短词的精确性与长词的容错性。原词自身会被排除。
     *
     * <p>编辑距离计算为 O(m×n) 时间、O(n) 空间的滚动数组实现，
     * 候选量受 {@code suggestLimit * 3} 约束，单次调用开销可控。
     *
     * <p><b>降级策略</b>：未注册 {@link SuggestStrategy} 或底层异常时返回空列表并记 warn 日志，
     * 不向上抛异常。
     *
     * @param keyword 用户输入的原始关键词，通常是零结果查询词；为 {@code null} 或空白时返回空列表
     * @return 按相似度升序排列的纠错候选，最多 {@code suggestLimit} 条；无合适候选时返回空列表而非 {@code null}
     */
    public List<String> didYouMean(String keyword) {
        if (keyword == null || keyword.isBlank()) return Collections.emptyList();
        try {
            Optional<SuggestStrategy> suggestStrategy = engineRegistry.getSuggestStrategy();
            if (suggestStrategy.isEmpty()) return Collections.emptyList();
            String loosePrefix = keyword.length() > 2 ? keyword.substring(0, 2) : keyword;
            SearchSuggestion suggestion = suggestStrategy.get().suggest(loosePrefix, properties.getSuggestLimit() * 3);
            if (suggestion == null || suggestion.getSuggestions() == null) return Collections.emptyList();

            String normalizedKeyword = keyword.trim().toLowerCase();
            return suggestion.getSuggestions().stream()
                    .filter(s -> s != null && !s.isBlank() && !s.equalsIgnoreCase(keyword))
                    .sorted((a, b) -> Integer.compare(
                            levenshtein(normalizedKeyword, a.toLowerCase()),
                            levenshtein(normalizedKeyword, b.toLowerCase())))
                    .filter(s -> levenshtein(normalizedKeyword, s.toLowerCase()) <= maxEditDistance(keyword))
                    .limit(properties.getSuggestLimit())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("[SuggestionService] 纠错建议失败: keyword={}", keyword, e);
            return Collections.emptyList();
        }
    }

    private int levenshtein(String s1, String s2) {
        int len1 = s1.length();
        int len2 = s2.length();
        if (len1 == 0) return len2;
        if (len2 == 0) return len1;

        int[] prev = new int[len2 + 1];
        int[] curr = new int[len2 + 1];
        for (int j = 0; j <= len2; j++) prev[j] = j;

        for (int i = 1; i <= len1; i++) {
            curr[0] = i;
            for (int j = 1; j <= len2; j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                curr[j] = Math.min(Math.min(prev[j] + 1, prev[j - 1] + cost), curr[j - 1] + 1);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[len2];
    }

    private int maxEditDistance(String keyword) {
        int len = keyword.length();
        if (len <= 2) return 1;
        if (len <= 5) return 2;
        return 3;
    }
}
