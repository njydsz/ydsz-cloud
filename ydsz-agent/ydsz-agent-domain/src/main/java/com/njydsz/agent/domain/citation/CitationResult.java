package com.njydsz.agent.domain.citation;

import java.util.Collections;
import java.util.List;

/**
 * 带引用的问答结果值对象。
 *
 * <p>封装 RAG 检索增强生成的完整结果，包括 LLM 回答和使用的引用列表。
 * 前端可基于 citation 列表构建引用抽屉，让用户追溯到原始知识。</p>
 *
 * @author ydsz-agent
 * @since 1.0.0
 */
public final class CitationResult {

    private final String answer;
    private final List<Citation> citations;
    private final boolean hasCitations;

    private CitationResult(String answer, List<Citation> citations) {
        this.answer = answer != null ? answer : "";
        this.citations = citations != null ? Collections.unmodifiableList(citations) : List.of();
        this.hasCitations = !this.citations.isEmpty();
    }

    /**
     * 创建带引用的结果。
     *
     * @param answer    LLM 生成的回答
     * @param citations 引用列表
     * @return CitationResult 实例
     */
    public static CitationResult of(String answer, List<Citation> citations) {
        return new CitationResult(answer, citations);
    }

    /**
     * 创建无引用的纯文本结果。
     *
     * @param answer LLM 生成的回答
     * @return CitationResult 实例
     */
    public static CitationResult withoutCitations(String answer) {
        return new CitationResult(answer, null);
    }

    public String getAnswer() {
        return answer;
    }

    public List<Citation> getCitations() {
        return citations;
    }

    public boolean isHasCitations() {
        return hasCitations;
    }

    /**
     * 获取引用数量。
     *
     * @return 引用数量
     */
    public int getCitationCount() {
        return citations.size();
    }

    /**
     * 根据相似度阈值过滤引用。
     *
     * @param minScore 最小相似度
     * @return 过滤后的新 CitationResult
     */
    public CitationResult filterByScore(double minScore) {
        List<Citation> filtered = citations.stream()
                .filter(c -> c.score() >= minScore)
                .toList();
        return new CitationResult(answer, filtered);
    }
}
