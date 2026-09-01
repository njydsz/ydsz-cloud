package com.njydsz.agent.server.rag;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import com.njydsz.common.docs.service.DocumentService;
import org.springframework.stereotype.Service;

import com.njydsz.agent.domain.rag.Retriever;
import com.njydsz.agent.domain.rag.TextChunk;
import com.njydsz.agent.domain.rag.VectorStore;
import com.njydsz.agent.server.config.AgentProperties;
import com.njydsz.common.docs.domain.DocumentContent;
import com.njydsz.common.docs.domain.DocumentParseResult;
import com.njydsz.common.docs.enums.DocumentFormat;

/**
 * RAG 检索服务
 *
 * <p>提供基于向量相似度的知识检索能力，将检索结果组装为 LLM 上下文。
 *
 * <p><b>DDD 合规：</b>通过 domain 层 {@link Retriever} 接口访问检索能力，不依赖 infra 实现。
 *
 * <h3>核心能力</h3>
 *
 * <ul>
 *   <li>{@link #retrieve} — 向量相似度检索
 *   <li>{@link #buildContext} — 将检索结果拼接为 LLM 上下文文本
 *   <li>{@link #retrieveAndBuild} — 检索 + 上下文组装一步到位
 *   <li>{@link #ingestByFileId} — 根据文件 ID 索引文档到 RAG 知识库
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Service
public class RagService {

  /** 默认返回 Top-5 召回；过小覆盖不足、过大引入噪声并增加上下文长度 */
  private static final int DEFAULT_TOP_K = 5;

  /** 默认最小相似度阈值 0.7：低于此分数视为不相关，过滤低质召回 */
  private static final double DEFAULT_MIN_SCORE = 0.7;

  /** 最大提取文本长度（1MB），保护向量库体积 */
  private static final int MAX_CONTENT_LENGTH = 1024 * 1024;

  /** 上下文模板固定开销 Token 估算（标题行 + 分隔线 + 结尾提示 ≈ 50 Token） */
  private static final int CONTEXT_TEMPLATE_OVERHEAD_TOKENS = 50;

  private final VectorStore vectorStore;
  private final ObjectProvider<Retriever> retrieverProvider;
  private final DocumentService documentService;
  private final DocumentIngestionService ingestionService;
  private final AgentProperties properties;

  public RagService(
      VectorStore vectorStore,
      ObjectProvider<Retriever> retrieverProvider,
      DocumentService documentService,
      DocumentIngestionService ingestionService,
      AgentProperties properties) {
    this.vectorStore = vectorStore;
    this.retrieverProvider = retrieverProvider;
    this.documentService = documentService;
    this.ingestionService = ingestionService;
    this.properties = properties;
  }

  /**
   * 向量相似度检索
   *
   * @param query 查询文本
   * @return 检索到的文本块列表
   */
  public List<TextChunk> retrieve(String query) {
    return retrieve(query, DEFAULT_TOP_K, DEFAULT_MIN_SCORE);
  }

  /**
   * 向量相似度检索
   *
   * @param query 查询文本
   * @param topK 返回前 K 条
   * @param minScore 最小相似度阈值
   * @return 检索到的文本块列表
   */
  public List<TextChunk> retrieve(String query, int topK, double minScore) {
    if (query == null || query.isBlank()) {
      return List.of();
    }
    Retriever retriever = retrieverProvider.getIfAvailable();
    List<TextChunk> chunks;
    if (retriever != null) {
      chunks = retriever.retrieve(query, topK, minScore);
    } else {
      chunks = vectorStore.search(query, topK, minScore);
    }
    log.info(
        "[RAG] 检索完成: query='{}', mode={}, results={}",
        truncate(query, 50),
        retriever != null ? "hybrid" : "vector",
        chunks.size());
    return chunks;
  }

  /**
   * 将检索结果组装为 LLM 上下文文本（Token 感知截断）。
   *
   * <p>按 Token 预算从前往后累加文本块，超出预算时截断当前块并追加省略标记。 保证输出的上下文总 Token 不超过 {@code contextTokenBudget}，
   * 避免检索结果占用过多上下文导致 LLM 回复质量下降。
   *
   * @param chunks 检索到的文本块
   * @return 上下文文本（含引用标注），Token 数不超过预算
   */
  public String buildContext(List<TextChunk> chunks) {
    if (chunks == null || chunks.isEmpty()) {
      return "";
    }
    int tokenBudget = properties.getRag().getContextTokenBudget();
    double tokenCharRatio = properties.getMemory().getTokenCharRatio();
    // 扣除模板固定开销
    int remainingTokens = tokenBudget - CONTEXT_TEMPLATE_OVERHEAD_TOKENS;
    StringBuilder sb = new StringBuilder();
    sb.append("以下是从知识库中检索到的相关内容：\n\n");
    for (int i = 0; i < chunks.size(); i++) {
      TextChunk chunk = chunks.get(i);
      // 计算当前块的 Token 数
      int chunkTokens = TokenEstimator.estimate(chunk.getContent(), tokenCharRatio);
      if (chunkTokens > remainingTokens) {
        // 超出预算：截断当前块或跳过
        if (remainingTokens > 50) {
          // 剩余预算足够容纳部分文本，截断并追加省略标记
          int maxChars = TokenEstimator.maxCharsForBudget(chunk.getContent(), remainingTokens, tokenCharRatio);
          sb.append("--- 参考资料 [").append(i + 1).append("] ---\n");
          if (chunk.getDocumentTitle() != null) {
            sb.append("来源: ").append(chunk.getDocumentTitle()).append("\n");
          }
          sb.append("内容: ")
              .append(chunk.getContent(), 0, Math.min(maxChars, chunk.getContent().length()))
              .append("...[已截断]\n\n");
        }
        // 预算已满，停止添加更多块
        break;
      }
      sb.append("--- 参考资料 [").append(i + 1).append("] ---\n");
      if (chunk.getDocumentTitle() != null) {
        sb.append("来源: ").append(chunk.getDocumentTitle()).append("\n");
      }
      if (chunk.getSource() != null) {
        sb.append("来源类型: ").append(chunk.getSource()).append("\n");
      }
      sb.append("内容: ").append(chunk.getContent()).append("\n\n");
      remainingTokens -= chunkTokens;
    }
    sb.append("--- 请基于以上参考资料回答用户问题。如果资料不足以回答，请如实说明。 ---\n");
    return sb.toString();
  }

  /**
   * 检索 + 上下文组装一步到位
   *
   * @param query 查询文本
   * @return LLM 上下文文本
   */
  public String retrieveAndBuild(String query) {
    List<TextChunk> chunks = retrieve(query);
    return buildContext(chunks);
  }

  /**
   * 获取检索结果摘要（用于前端展示引用来源）
   *
   * @param chunks 检索到的文本块
   * @return 引用摘要列表
   */
  public List<Citation> getCitations(List<TextChunk> chunks) {
    List<Citation> citations = new ArrayList<>();
    if (chunks == null) {
      return citations;
    }
    for (int i = 0; i < chunks.size(); i++) {
      TextChunk chunk = chunks.get(i);
      citations.add(
          new Citation(
              i + 1,
              chunk.getDocumentId(),
              chunk.getDocumentTitle(),
              chunk.getSource(),
              truncate(chunk.getContent(), 200)));
    }
    return citations;
  }

  private String truncate(String text, int maxLen) {
    if (text == null) {
      return "";
    }
    return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
  }

  /**
   * 根据文件 ID 索引文档到 RAG 知识库（跨模块事件触发）。
   *
   * <p>当 nextwiki 模块发布 FILE_UPLOADED 事件时，Agent 模块监听并调用此方法。 流程：获取文件内容 → 解析文档（{@link
   * DocumentService}）→ 分块/向量化/存储（{@link DocumentIngestionService}）。
   *
   * <p><b>文件获取说明：</b>当前通过 aggregateId（即 fileId）标识文件， 实际获取文件内容需调用 nextwiki 模块的 Feign API（待实现 {@code
   * FileContentFeignClient}）。 在 Feign 客户端就绪前，可使用 {@link #ingestFromStream} 方法直接摄入已获取的文件流。
   *
   * @param fileId 文件 ID（对应 nextwiki 模块的文件节点 ID）
   * @see #ingestFromStream(InputStream, String, String)
   * @throws UnsupportedOperationException Feign 客户端未就绪时抛出，提示使用 ingestFromStream 替代
   */
  public void ingestByFileId(String fileId) {
    log.warn("[RagService] ingestByFileId 暂未实现（待 nextwiki 模块暴露文件内容 Feign API 后补充），"
        + "fileId={}。请使用 ingestFromStream(InputStream, String, String) 替代。", fileId);
    throw new UnsupportedOperationException(
        "ingestByFileId 暂未实现：待 FileContentFeignClient 就绪后补充。"
            + "请使用 ingestFromStream 直接摄入文件流。fileId=" + fileId);
  }

  /**
   * 从文件流摄入文档到 RAG 知识库。
   *
   * <p>支持 PDF / Office / 纯文本 / Markdown / HTML 等多种格式， 由 {@link DocumentService#parseAndPreprocess}
   * 自动检测格式并解析。
   *
   * <p>流程：文档解析 → 纯文本提取 → 分块 → 向量化 → 存入向量库。
   *
   * @param inputStream 文件输入流（调用方负责关闭）
   * @param fileName 原始文件名（含后缀，用于格式检测）
   * @param documentId 文档 ID（用于关联向量块）
   * @return 摄入的文本块数；解析失败返回 0
   */
  public int ingestFromStream(InputStream inputStream, String fileName, String documentId) {
    log.info("[RagService] 开始摄入文件流: fileName={}, documentId={}", fileName, documentId);

    // 格式预检：快速跳过不支持的格式
    if (DocumentFormat.fromFileName(fileName) == DocumentFormat.UNKNOWN) {
      log.info("[RagService] 不支持的格式，跳过摄入: fileName={}", fileName);
      return 0;
    }

    // 使用 common-docs 解析文档
    DocumentParseResult parseResult =
        documentService.parseAndPreprocess(inputStream, fileName, null);
    if (!parseResult.isSuccess()) {
      log.warn(
          "[RagService] 文档解析失败: fileName={}, error={}", fileName, parseResult.getErrorMessage());
      return 0;
    }

    DocumentContent docContent = parseResult.getContent();
    if (docContent == null || docContent.getText() == null || docContent.getText().isEmpty()) {
      log.warn("[RagService] 文档内容为空: fileName={}", fileName);
      return 0;
    }

    String text = docContent.getText();
    // 限制最大长度
    if (text.length() > MAX_CONTENT_LENGTH) {
      text = text.substring(0, MAX_CONTENT_LENGTH);
    }

    // 委托 DocumentIngestionService 完成分块 + 向量化 + 存储
    int count = ingestionService.ingest(documentId, text, fileName, "nextwiki");
    log.info("[RagService] 文件流摄入完成: fileName={}, chunks={}", fileName, count);
    return count;
  }

  /**
   * RAG 答案引用来源。
   *
   * @param index 引用序号（从 0 开始，对应答案中的标注）
   * @param documentId 被引用文档 ID
   * @param documentTitle 被引用文档标题
   * @param source 内容来源（文件路径/URL 等）
   * @param snippet 命中的原文片段（用于人工核对）
   */
  public record Citation(
      int index, String documentId, String documentTitle, String source, String snippet) {}
}
