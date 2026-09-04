package com.njydsz.agent.domain.citation;

import java.util.List;


/**
 * 引用生成器网关接口。
 *
 * <p>定义从 RAG 检索结果生成引用信息的操作。
 * 将 TextChunk 转换为前端可展示的 Citation 对象。</p>
 *
 * @author ydsz-agent
 * @since 26.09.01
 */
public interface CitationGenerator {

    /**
     * 从检索到的文本块列表生成引用列表。
     *
     * @param chunks 检索到的文本块
     * @return 引用列表
     */
    List<Citation> generateCitations(List<TextChunk> chunks);

    /**
     * 从单个文本块生成引用。
     *
     * @param chunk 文本块
     * @return Citation 对象
     */
    Citation generateCitation(TextChunk chunk);
}
