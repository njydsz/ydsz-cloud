package com.njydsz.agent.domain.gateway;

import java.util.List;
import java.util.function.Consumer;

import com.njydsz.agent.domain.model.ChatChunk;
import com.njydsz.agent.domain.model.ChatRequest;
import com.njydsz.agent.domain.model.ChatResponse;

/**
 * LLM 客户端接口（Provider 抽象层）
 *
 * <p>统一抽象不同 LLM Provider 的调用接口。 实现类通过 {@code @Component}
 * 注册，由 {@code LlmClientRouter} 按模型配置路由。
 *
 * <h3>核心能力</h3>
 *
 * <ul>
 *   <li>{@link #chat} — 同步调用，等待完整响应
 *   <li>{@link #stream} — 流式调用，逐 token 回调消费
 *   <li>{@link #supports} — 判断是否支持指定模型
 * </ul>
 *
 * <h3>实现约束</h3>
 *
 * <ul>
 *   <li>实现必须是线程安全的
 *   <li>单次调用应在配置的超时时间内完成
 *   <li>网络异常应包装为 {@code LlmException} 抛出，不返回 null
 * </ul>
 *
 * @author ydsz-team
 * @since 26.10.01
 */
public interface LlmClient {

  /**
   * 同步聊天补全
   *
   * @param request 聊天请求
   * @return 聊天响应
   * @throws LlmException LLM 调用异常
   */
  ChatResponse chat(ChatRequest request);

  /**
   * 流式聊天补全（SSE）
   *
   * <p>逐 token 回调 {@code chunkConsumer}，调用方在回调中处理增量内容。 最后一个 chunk 的 {@link ChatChunk#isFinished()}
   * 返回 true。
   *
   * @param request 聊天请求（stream 字段将被强制设为 true）
   * @param chunkConsumer 流式片段消费者
   * @throws LlmException LLM 调用异常
   */
  void stream(ChatRequest request, Consumer<ChatChunk> chunkConsumer);

  /**
   * 判断是否支持指定模型
   *
   * @param modelId 模型标识
   * @return true=支持
   */
  boolean supports(String modelId);

  /**
     * Provider 标识
   *
   * @return Provider 标识
   */
  String getProvider();

  /**
   * 获取当前客户端支持的全部 Provider 名称列表。
   *
   * <p>默认实现返回仅包含自身 {@link #getProvider()} 的单元素列表（单 Provider 客户端通用）；
   * 路由器实现（{@code LlmClientRouter}）覆写为返回所有已注册的 Provider 名称。
   *
   * <p>该方法将 Provider 枚举能力下沉至 domain 接口，消除 web 层对 infra 路由器类型的显式依赖。
   *
   * @return Provider 名称列表（不可为 null，至少包含一个元素）
   */
  default List<String> getAvailableProviders() {
    return List.of(getProvider());
  }

  /**
   * 文本向量化（Embedding）
   *
   * <p>将输入文本转化为高维浮点向量，用于语义相似度计算。 Provider 需兼容 OpenAI Embeddings API 格式（{@code POST /embeddings}）。
   *
   * @param text 待向量化的文本（非空）
   * @return 浮点向量表示
   * @throws LlmException 向量化失败时抛出
   */
  List<Float> embed(String text);
}
