package com.njydsz.pmis.agent.rag;

import com.njydsz.pmis.agent.config.RAGProperties;
import com.njydsz.pmis.agent.engine.embedding.MockEmbeddingProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Retriever 检索器单元测试（P3-1 落地）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-1)
 */
@DisplayName("Retriever RAG 检索器")
class RetrieverTest {

    private MockEmbeddingProvider embeddingProvider;
    private InMemoryVectorStore vectorStore;
    private RAGProperties properties;
    private Retriever retriever;

    @BeforeEach
    void setUp() {
        embeddingProvider = new MockEmbeddingProvider();
        vectorStore = new InMemoryVectorStore();
        properties = new RAGProperties();
        properties.setTopK(3);
        properties.setMinScore(0.0);
        retriever = new Retriever(embeddingProvider, vectorStore, properties);

        // 预置数据
        vectorStore.store("kb-1", "doc-1", 0, "项目风险管理规范",
                embeddingProvider.embed("项目风险管理规范"), 10);
        vectorStore.store("kb-1", "doc-1", 1, "资源配置优化指南",
                embeddingProvider.embed("资源配置优化指南"), 10);
        vectorStore.store("kb-1", "doc-2", 0, "进度控制方法论",
                embeddingProvider.embed("进度控制方法论"), 10);
    }

    @Nested
    @DisplayName("retrieve 检索")
    class RetrieveTest {

        @Test
        @DisplayName("正常检索返回 top-k 结果")
        void shouldReturnTopKResults() {
            List<RetrievedChunk> results = retriever.retrieve("kb-1", "项目风险管理规范");

            assertThat(results).isNotEmpty();
            // 第一个应该是完全匹配的
            assertThat(results.get(0).getContent()).isEqualTo("项目风险管理规范");
        }

        @Test
        @DisplayName("null query 返回空列表")
        void nullQueryShouldReturnEmptyList() {
            List<RetrievedChunk> results = retriever.retrieve("kb-1", null);
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("空 query 返回空列表")
        void blankQueryShouldReturnEmptyList() {
            List<RetrievedChunk> results = retriever.retrieve("kb-1", "  ");
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("按 topK 限制返回数量")
        void shouldLimitByTopK() {
            properties.setTopK(2);
            List<RetrievedChunk> results = retriever.retrieve("kb-1", "项目风险");

            assertThat(results).hasSizeLessThanOrEqualTo(2);
        }

        @Test
        @DisplayName("按 minScore 过滤低分结果")
        void shouldFilterByMinScore() {
            // 设置高阈值，应过滤掉所有结果（mock 向量无语义相似性）
            properties.setMinScore(0.99);
            List<RetrievedChunk> results = retriever.retrieve("kb-1", "完全不相关的查询文本");

            // mock 向量基于哈希，完全相同的文本相似度为 1，其他通常较低
            // 这里不严格断言为空，但分数低于阈值的应被过滤
            for (RetrievedChunk chunk : results) {
                if (chunk.getScore() != null) {
                    assertThat(chunk.getScore()).isGreaterThanOrEqualTo(0.99);
                }
            }
        }

        @Test
        @DisplayName("空知识库返回空列表")
        void emptyKnowledgeBaseShouldReturnEmptyList() {
            List<RetrievedChunk> results = retriever.retrieve("non-existent-kb", "任意查询");
            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("retrieveAsContext 拼接上下文")
    class RetrieveAsContextTest {

        @Test
        @DisplayName("有结果时拼接为带序号的文本")
        void shouldFormatWithContextNumbers() {
            String context = retriever.retrieveAsContext("kb-1", "项目风险管理规范");

            assertThat(context).isNotEmpty();
            assertThat(context).contains("[1]");
            assertThat(context).contains("项目风险管理规范");
        }

        @Test
        @DisplayName("无结果时返回空字符串")
        void emptyResultsShouldReturnEmptyString() {
            String context = retriever.retrieveAsContext("non-existent", "任意查询");
            assertThat(context).isEmpty();
        }

        @Test
        @DisplayName("null query 返回空字符串")
        void nullQueryShouldReturnEmptyString() {
            String context = retriever.retrieveAsContext("kb-1", null);
            assertThat(context).isEmpty();
        }
    }
}
