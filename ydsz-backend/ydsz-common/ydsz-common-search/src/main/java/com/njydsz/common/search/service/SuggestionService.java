package com.njydsz.common.search.service;

import java.util.ArrayList;
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
 * 搜索建议服务接口。
 * <p>输入前缀返回候选词/热门词。
 *
 * @author ydsz-team
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
