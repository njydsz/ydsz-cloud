package com.njydsz.agent.server.rag;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;

import com.njydsz.agent.domain.rag.TextChunk;
import com.njydsz.agent.domain.rag.VectorStore;
import com.njydsz.agent.infra.rag.HybridRetriever;

/**
 * RAG 检索服务
 *
 * <p>提供基于向量相似度的知识检索能力，将检索结果组装为 LLM 上下文。
 *
 * <h3>核心能力</h3>
 * <ul>
 *   <li>{@link #retrieve} — 向量相似度检索</li>
 *   <li>{@link #buildContext} — 将检索结果拼接为 LLM 上下文文本</li>
 *   <li>{@link #retrieveAndBuild} — 检索 + 上下文组装一步到位</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);
    // 默认返回 Top-5 召回；过小覆盖不足、过大引入噪声并增加上下文长度
    private static final int DEFAULT_TOP_K = 5;
    // 默认最小相似度阈值 0.7：低于此分数视为不相关，过滤低质召回
    private static final double DEFAULT_MIN_SCORE = 0.7;

    private final VectorStore vectorStore;
    private final ObjectProvider<HybridRetriever> hybridRetrieverProvider;

    public RagService(VectorStore vectorStore,
                      ObjectProvider<HybridRetriever> hybridRetrieverProvider) {
        this.vectorStore = vectorStore;
        this.hybridRetrieverProvider = hybridRetrieverProvider;
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
     * @param query    查询文本
     * @param topK     返回前 K 条
     * @param minScore 最小相似度阈值
     * @return 检索到的文本块列表
     */
    public List<TextChunk> retrieve(String query, int topK, double minScore) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        HybridRetriever hybridRetriever = hybridRetrieverProvider.getIfAvailable();
        List<TextChunk> chunks;
        if (hybridRetriever != null) {
            chunks = hybridRetriever.retrieve(query, topK, minScore);
        } else {
            chunks = vectorStore.search(query, topK, minScore);
        }
        log.info("[RAG] 检索完成: query='{}', mode={}, results={}",
                truncate(query, 50), hybridRetriever != null ? "hybrid" : "vector", chunks.size()); // 日志中查询仅保留前 50 字符，避免长文本刷屏
        return chunks;
    }

    /**
     * 将检索结果组装为 LLM 上下文文本
     *
     * @param chunks 检索到的文本块
     * @return 上下文文本（含引用标注）
     */
    public String buildContext(List<TextChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("以下是从知识库中检索到的相关内容：\n\n");
        for (int i = 0; i < chunks.size(); i++) {
            TextChunk chunk = chunks.get(i);
            sb.append("--- 参考资料 [").append(i + 1).append("] ---\n");
            if (chunk.getDocumentTitle() != null) {
                sb.append("来源: ").append(chunk.getDocumentTitle()).append("\n");
            }
            if (chunk.getSource() != null) {
                sb.append("来源类型: ").append(chunk.getSource()).append("\n");
            }
            sb.append("内容: ").append(chunk.getContent()).append("\n\n");
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
            citations.add(new Citation(
                    i + 1,
                    chunk.getDocumentId(),
                    chunk.getDocumentTitle(),
                    chunk.getSource(),
                    truncate(chunk.getContent(), 200))); // 引用摘要截取到 200 字符，供前端展示
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
     * <p>当 nextwiki 模块发布 FILE_UPLOADED 事件时，Agent 模块监听并调用此方法。
     * 实际实现需通过 Feign 调用 nextwiki 服务获取文件内容，再做文档解析和向量化。
     *
     * @param fileId 文件 ID
     */
    public void ingestByFileId(String fileId) {
        log.info("[RagService] 接收文件索引请求: fileId={}", fileId);
        // TODO: 通过 Feign 调用 nextwiki 获取文件内容 → 文档解析 → 向量化 → 存入 VectorStore
    }

    public record Citation(int index, String documentId, String documentTitle,
                            String source, String snippet) {}
}
