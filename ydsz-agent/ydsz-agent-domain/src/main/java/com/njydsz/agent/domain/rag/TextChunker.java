package com.njydsz.agent.domain.rag;

import java.util.List;

/**
 * 文本分块器接口
 *
 * <p>将长文本切分为合适大小的块（chunk），以便向量化处理。
 *
 * <p><b>线程安全</b>：分块器通常为无状态单例，实现须保证并发 chunk 调用的线程安全（不依赖可变实例字段）。
 *
 * <p><b>与 ydsz-common-docs 同名类的关系（ADR-5，见 docs/ADR-2026-09-12_公共能力重复实现收敛决策.md）：</b>
 * 本接口面向 RAG 向量化分块（产出 {@link TextChunk} 供 VectorStore/Reranker 消费），
 * common-docs 的 {@code TextChunker} 面向文档解析流水线（PII 检测/结构抽取）。二者语义不同层、
 * 目标不同，<b>不合并</b>；但实现类的切段算法若与 common-docs 逐行重复，必须改为委托调用（§33.4）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface TextChunker {

  /**
   * 将文本分块
   *
   * @param text 原始文本
   * @param documentId 文档 ID（用于关联）
   * @return 文本块列表
   */
  List<TextChunk> chunk(String text, String documentId);

  /**
   * 将文本分块（含文档元信息）
   *
   * @param text 原始文本
   * @param documentId 文档 ID
   * @param documentTitle 文档标题
   * @param source 来源（如 "nextwiki"、"project"）
   * @return 文本块列表
   */
  List<TextChunk> chunk(String text, String documentId, String documentTitle, String source);
}
