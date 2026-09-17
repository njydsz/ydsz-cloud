package com.njydsz.agent.domain.rag;

/**
 * RAG 全链路调试信息查询接口（domain 层契约）
 *
 * <p>由 infra 层的 {@code HybridRetriever} 实现，供 server 层可观测性服务查询最近一次检索的耗时分解。 通过此接口解耦 server 层对 infra 层检索实现的直接依赖（DDD 分层合规）。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
public interface RagDebugEnquirer {

  /**
   * 获取最近一次检索的全链路调试信息。
   *
   * @return 最近一次检索的调试信息；如从未检索则返回默认值对象
   */
  RagDebugInfo getLastDebugInfo();
}
