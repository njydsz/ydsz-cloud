package com.remisoft.common.search.core;

import com.remisoft.common.search.api.SearchSuggestion;

/**
 * 搜索建议策略 SPI
 * <p>
 * 支持搜索建议（自动补全）的引擎实现此接口。
 *
 * @author remi-team
 * @since 1.0.0
 * @see SearchStrategy
 */
public interface SuggestStrategy {

    /**
     * 搜索建议（自动补全）
     *
     * @param prefix 前缀
     * @param limit  最大返回数
     * @return 搜索建议
     */
    SearchSuggestion suggest(String prefix, int limit);
}
