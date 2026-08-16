package com.njydsz.agent.infra.rag;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.njydsz.agent.domain.rag.Reranker;
import com.njydsz.agent.domain.rag.TextChunk;

/**
 * 恒等 Reranker（关闭重排序时的默认实现）
 *
 * <p>仅做截断处理，不做任何重排。当未配置 Reranker 或配置为 {@code none} 时使用，
 * 保持与原始混合检索一致的输出。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class IdentityReranker implements Reranker {

    private static final Logger log = LoggerFactory.getLogger(IdentityReranker.class);

    @Override
    public List<TextChunk> rerank(String query, List<TextChunk> chunks, int topK) {
        if (chunks.size() <= topK) {
            return chunks;
        }
        return chunks.subList(0, topK);
    }

    @Override
    public String getType() {
        return "identity";
    }
}
