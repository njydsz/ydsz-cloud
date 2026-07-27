package com.njydsz.common.search.service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.njydsz.common.search.api.SearchSuggestion;
import com.njydsz.common.search.config.SearchProperties;
import com.njydsz.common.search.core.SearchEngineRegistry;
import com.njydsz.common.search.core.SuggestStrategy;

import lombok.extern.slf4j.Slf4j;

/**
 * 搜索建议服务
 *
 * @author ydsz-team
 * @since 1.3.0
 */
@Slf4j
public class SuggestionService {

    private final SearchEngineRegistry engineRegistry;
    private final SearchProperties properties;

    public SuggestionService(SearchEngineRegistry engineRegistry, SearchProperties properties) {
        this.engineRegistry = engineRegistry;
        this.properties = properties;
    }

    public List<String> autocomplete(String prefix) {
        if (prefix == null || prefix.isBlank()) return Collections.emptyList();
        try {
            Optional<SuggestStrategy> suggestStrategy = engineRegistry.getSuggestStrategy();
            if (suggestStrategy.isEmpty()) return Collections.emptyList();
            SearchSuggestion suggestion = suggestStrategy.get().suggest(prefix, properties.getSuggestLimit());
            return suggestion != null ? suggestion.getSuggestions() : Collections.emptyList();
        } catch (Exception e) {
            log.warn("[SuggestionService] 自动补全失败: prefix={}", prefix, e);
            return Collections.emptyList();
        }
    }

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
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= s2.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i - 1][j - 1] + cost), dp[i][j - 1] + 1);
            }
        }
        return dp[s1.length()][s2.length()];
    }

    private int maxEditDistance(String keyword) {
        int len = keyword.length();
        if (len <= 2) return 1;
        if (len <= 5) return 2;
        return 3;
    }
}
