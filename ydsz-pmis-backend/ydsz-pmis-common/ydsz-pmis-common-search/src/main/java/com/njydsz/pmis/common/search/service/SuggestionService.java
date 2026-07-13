package com.njydsz.pmis.common.search.service;

import java.util.Collections;
import java.util.List;

import com.njydsz.pmis.common.search.api.SearchSuggestion;
import com.njydsz.pmis.common.search.config.SearchProperties;
import com.njydsz.pmis.common.search.core.SearchEngine;

import lombok.extern.slf4j.Slf4j;

/**
 * 搜索建议服务
 * <p>
 * 提供搜索自动补全和"您是不是要找"纠错建议。
 *
 * @author ydsz-pmis-team
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
     *
     * @param keyword 原始关键词
     * @return 建议列表
     */
    public List<String> didYouMean(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return Collections.emptyList();
        }
        try {
            SearchSuggestion suggestion = searchEngine.suggest(keyword, properties.getSuggestLimit());
            if (suggestion != null) {
                suggestion.setType(SearchSuggestion.SuggestionType.DID_YOU_MEAN);
                return suggestion.getSuggestions();
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.warn("[SuggestionService] 纠错建议失败: keyword={}", keyword, e);
            return Collections.emptyList();
        }
    }
}
