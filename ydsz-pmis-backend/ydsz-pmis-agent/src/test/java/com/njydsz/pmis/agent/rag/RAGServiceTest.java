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
 * RAGService 入库服务单元测试（P3-1 落地）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-1)
 */
@DisplayName("RAGService 入库服务")
class RAGServiceTest {

    private MockEmbeddingProvider embeddingProvider;
    private InMemoryVectorStore vectorStore;
    private RAGProperties properties;
    private RAGService ragService;

    @BeforeEach
    void setUp() {
        embeddingProvider = new MockEmbeddingProvider();
        vectorStore = new InMemoryVectorStore();
        properties = new RAGProperties();
        properties.setChunkSize(100);
        properties.setChunkOverlap(10);
        properties.setTopK(3);
        ragService = new RAGService(embeddingProvider, vectorStore, properties);
    }

    @Nested
    @DisplayName("ingest 文档入库")
    class IngestTest {

        @Test
        @DisplayName("正常文档入库返回分块数")
        void shouldIngestAndReturnChunkCount() {
            String content = "这是第一段内容。这是第二段内容。这是第三段内容。";
            int chunks = ragService.ingest("kb-1", "doc-1", content);

            assertThat(chunks).isPositive();
            assertThat(vectorStore.size()).isEqualTo(chunks);
        }

        @Test
        @DisplayName("长文档按 chunkSize 切分多个分块")
        void shouldSplitLongDocumentIntoMultipleChunks() {
            properties.setChunkSize(10);
            properties.setChunkOverlap(0);
            String content = "0123456789abcdefghij";  // 20 字符

            int chunks = ragService.ingest("kb-1", "doc-1", content);

            assertThat(chunks).isEqualTo(2);
            assertThat(vectorStore.size()).isEqualTo(2);
        }

        @Test
        @DisplayName("空内容返回 0")
        void emptyContentShouldReturn0() {
            int chunks = ragService.ingest("kb-1", "doc-1", "");
            assertThat(chunks).isZero();
        }

        @Test
        @DisplayName("null 内容返回 0")
        void nullContentShouldReturn0() {
            int chunks = ragService.ingest("kb-1", "doc-1", null);
            assertThat(chunks).isZero();
        }

        @Test
        @DisplayName("空白内容返回 0")
        void blankContentShouldReturn0() {
            int chunks = ragService.ingest("kb-1", "doc-1", "   \n\n  ");
            assertThat(chunks).isZero();
        }

        @Test
        @DisplayName("入库后可通过检索验证")
        void ingestedContentShouldBeRetrievable() {
            String content = "项目风险管理规范";
            ragService.ingest("kb-1", "doc-1", content);

            Retriever retriever = new Retriever(embeddingProvider, vectorStore, properties);
            List<RetrievedChunk> results = retriever.retrieve("kb-1", "项目风险管理规范");

            assertThat(results).isNotEmpty();
            assertThat(results.get(0).getContent()).isEqualTo("项目风险管理规范");
        }

        @Test
        @DisplayName("入库异常不传播（单个分块失败不影响其他）")
        void shouldNotPropagateException() {
            // 使用会抛异常的 VectorStore
            VectorStore failingStore = new VectorStore() {
                @Override
                public String store(String kb, String doc, int idx, String content, float[] emb, int tokens) {
                    if (idx == 1) {
                        throw new RuntimeException("模拟存储失败");
                    }
                    return "chunk-" + idx;
                }
                @Override
                public List<RetrievedChunk> search(String kb, float[] q, int k) { return List.of(); }
                @Override
                public int deleteByDocument(String doc) { return 0; }
                @Override
                public int deleteByKnowledgeBase(String kb) { return 0; }
                @Override
                public int countByKnowledgeBase(String kb) { return 0; }
            };
            RAGService service = new RAGService(embeddingProvider, failingStore, properties);

            // 应正常返回分块数，不抛异常
            String content = "内容1。内容2。内容3。";
            int chunks = service.ingest("kb-1", "doc-1", content);
            assertThat(chunks).isPositive();
        }
    }

    @Nested
    @DisplayName("batchRetrieve 批量检索")
    class BatchRetrieveTest {

        @Test
        @DisplayName("多个 query 检索并去重")
        void shouldRetrieveAndDeduplicate() {
            // 入库
            ragService.ingest("kb-1", "doc-1", "项目风险管理规范");
            ragService.ingest("kb-1", "doc-2", "资源配置优化指南");

            // 多个 query 检索
            List<String> queries = List.of("项目风险管理规范", "资源配置优化指南");
            List<RetrievedChunk> results = ragService.batchRetrieve("kb-1", queries, 3);

            assertThat(results).isNotEmpty();
        }

        @Test
        @DisplayName("空 query 列表返回空结果")
        void emptyQueriesShouldReturnEmptyList() {
            ragService.ingest("kb-1", "doc-1", "内容");
            List<RetrievedChunk> results = ragService.batchRetrieve("kb-1", List.of(), 3);
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("null query 列表返回空结果")
        void nullQueriesShouldReturnEmptyList() {
            ragService.ingest("kb-1", "doc-1", "内容");
            List<RetrievedChunk> results = ragService.batchRetrieve("kb-1", null, 3);
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("批量检索后恢复原 topK 配置")
        void shouldRestoreOriginalTopK() {
            properties.setTopK(5);
            ragService.ingest("kb-1", "doc-1", "内容");

            ragService.batchRetrieve("kb-1", List.of("内容"), 2);

            assertThat(properties.getTopK()).isEqualTo(5);
        }
    }
}
