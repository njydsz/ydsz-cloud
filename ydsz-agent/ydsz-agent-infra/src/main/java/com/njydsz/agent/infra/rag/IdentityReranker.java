package com.njydsz.agent.infra.rag;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.agent.domain.rag.Reranker;
import com.njydsz.agent.domain.rag.TextChunk;

/**
 * 恒等 Reranker（关闭重排序时的默认实现）
 *
 * <p>仅做截断处理，不做任何重排。当未配置 Reranker 或配置为 {@code none} 时使用， 保持与原始混合检索一致的输出。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class IdentityReranker implements Reranker {

  /**
   * 不做重排序，仅截断至 topK 条。
   *
   * @param query 用户查询（未使用）
   * @param chunks 待截断的文本块列表
   * @param topK 返回条数上限
   * @return 截断后的文本块列表
   */
  @Override
  public List<TextChunk> rerank(String query, List<TextChunk> chunks, int topK) {
    if (chunks.size() <= topK) {
      return chunks;
    }
    return chunks.subList(0, topK);
  }

  /**
   * 返回重排序器类型标识。
   *
   * @return "identity" — 恒等重排序
   */
  @Override
  public String getType() {
    return "identity";
  }
}
