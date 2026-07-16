package com.njydsz.common.search.service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.njydsz.common.search.api.SearchSuggestion;
import com.njydsz.common.search.config.SearchProperties;
import com.njydsz.common.search.core.SearchEngine;

import lombok.extern.slf4j.Slf4j;

/**
 * 搜索建议服务
 * <p>
 * 提供搜索自动补全和"您是不是要找"纠错建议。
 * 自动补全基于前缀匹配，纠错建议基于 Levenshtein 编辑距离算法。
 *
 * @author ydsz-team
 * @since 1.4.0
 */
@Slf4j
public class SuggestionService {

    private final SearchEngine searchEngine;
    private final SearchProperties properties;

    public SuggestionService(SearchEngine searchEngine, SearchProperties properties) {
        this.searchEngine = searchEngine;
        this.properties = properties;
    }

    /**
     * 自动补全
     * <p>
     * 基于前缀匹配，返回以用户输入开头的搜索建议。
     *
     * @param prefix 用户输入前缀
     * @return 建议列表
     */
    public List<String> autocomplete(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return Collections.emptyList();
        }
        try {
            SearchSuggestion suggestion = searchEngine.suggest(prefix, properties.getSuggestLimit());
            return suggestion != null ? suggestion.getSuggestions() : Collections.emptyList();
        } catch (Exception e) {
            log.warn("[SuggestionService] 自动补全失败: prefix={}", prefix, e);
            return Collections.emptyList();
        }
    }

    /**
     * "您是不是要找"（零结果纠错）
     * <p>
     * 使用 Levenshtein 编辑距离算法，从索引标题中找出与输入关键词最相似的词。
     * 与 autocomplete 不同，didYouMean 不要求前缀匹配，而是找拼写相近的词。
     *
     * @param keyword 原始关键词
     * @return 纠错建议列表
     */
    public List<String> didYouMean(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return Collections.emptyList();
        }
        try {
            // 获取索引中的候选词（使用宽松前缀匹配获取候选集）
            String loosePrefix = keyword.length() > 2 ? keyword.substring(0, 2) : keyword;
            SearchSuggestion suggestion = searchEngine.suggest(loosePrefix, properties.getSuggestLimit() * 3);
            if (suggestion == null || suggestion.getSuggestions() == null) {
                return Collections.emptyList();
            }

            // 使用 Levenshtein 距离排序，找出最相似的词
            String normalizedKeyword = keyword.trim().toLowerCase();
            return suggestion.getSuggestions().stream()
                    .filter(s -> s != null && !s.isBlank())
                    .filter(s -> !s.equalsIgnoreCase(keyword)) // 排除完全相同的词
                    .sorted((a, b) -> {
                        int distA = levenshtein(normalizedKeyword, a.toLowerCase());
                        int distB = levenshtein(normalizedKeyword, b.toLowerCase());
                        return Integer.compare(distA, distB);
                    })
                    .filter(s -> levenshtein(normalizedKeyword, s.toLowerCase()) <= maxEditDistance(keyword))
                    .limit(properties.getSuggestLimit())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("[SuggestionService] 纠错建议失败: keyword={}", keyword, e);
            return Collections.emptyList();
        }
    }

    /**
     * Levenshtein 编辑距离算法
     * <p>
     * 计算两个字符串之间的最小编辑操作数（插入、删除、替换）。
     *
     * @param s1 字符串1
     * @param s2 字符串2
     * @return 编辑距离
     */
    private int levenshtein(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i - 1][j - 1] + cost),
                        dp[i][j - 1] + 1);
            }
        }
        return dp[s1.length()][s2.length()];
    }

    /**
     * 根据关键词长度计算最大允许编辑距离
     */
    private int maxEditDistance(String keyword) {
        int len = keyword.length();
        if (len <= 2) return 1;
        if (len <= 5) return 2;
        return 3;
    }
}
