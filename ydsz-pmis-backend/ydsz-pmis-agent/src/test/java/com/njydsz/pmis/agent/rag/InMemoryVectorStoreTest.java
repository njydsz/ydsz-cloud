package com.njydsz.pmis.agent.rag;

import com.njydsz.pmis.agent.engine.embedding.MockEmbeddingProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * InMemoryVectorStore 单元测试（P3-1 落地）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-1)
 */
@DisplayName("InMemoryVectorStore 内存向量存储")
class InMemoryVectorStoreTest {

    private InMemoryVectorStore store;
    private final MockEmbeddingProvider embeddingProvider = new MockEmbeddingProvider();

    @BeforeEach
    void setUp() {
        store = new InMemoryVectorStore();
    }

    @Nested
    @DisplayName("store 存储")
    class StoreTest {

        @Test
        @DisplayName("存储分块返回 ID")
        void shouldReturnChunkId() {
            float[] embedding = embeddingProvider.embed("测试");
            String id = store.store("kb-1", "doc-1", 0, "内容", embedding, 10);

            assertThat(id).isNotNull();
            assertThat(store.size()).isEqualTo(1);
        }

        @Test
        @DisplayName("多次存储产生不同 ID")
        void multipleStoreShouldReturnDifferentIds() {
            float[] e = embeddingProvider.embed("测试");
            String id1 = store.store("kb-1", "doc-1", 0, "内容1", e, 5);
            String id2 = store.store("kb-1", "doc-1", 1, "内容2", e, 5);

            assertThat(id1).isNotEqualTo(id2);
            assertThat(store.size()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("search 检索")
    class SearchTest {

        @Test
        @DisplayName("null 查询向量返回空列表")
        void nullQueryShouldReturnEmptyList() {
            store.store("kb-1", "doc-1", 0, "内容", embeddingProvider.embed("测试"), 5);
            List<RetrievedChunk> results = store.search("kb-1", null, 5);
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("topK <= 0 返回空列表")
        void nonPositiveTopKShouldReturnEmptyList() {
            store.store("kb-1", "doc-1", 0, "内容", embeddingProvider.embed("测试"), 5);
            List<RetrievedChunk> results = store.search("kb-1", embeddingProvider.embed("测试"), 0);
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("检索结果按相似度降序排列")
        void shouldOrderByScoreDesc() {
            // 三个不同内容的分块
            store.store("kb-1", "doc-1", 0, "风险管理", embeddingProvider.embed("风险管理"), 5);
            store.store("kb-1", "doc-1", 1, "资源分配", embeddingProvider.embed("资源分配"), 5);
            store.store("kb-1", "doc-1", 2, "进度控制", embeddingProvider.embed("进度控制"), 5);

            // 查询"风险管理"应匹配到第一个
            List<RetrievedChunk> results = store.search("kb-1", embeddingProvider.embed("风险管理"), 3);

            assertThat(results).hasSize(3);
            // 第一个应该是"风险管理"自己（相似度最高）
            assertThat(results.get(0).getContent()).isEqualTo("风险管理");
            assertThat(results.get(0).getScore()).isCloseTo(1.0,
                    org.assertj.core.data.Offset.offset(1e-6));
        }

        @Test
        @DisplayName("按知识库 ID 过滤")
        void shouldFilterByKnowledgeBaseId() {
            store.store("kb-1", "doc-1", 0, "内容1", embeddingProvider.embed("测试1"), 5);
            store.store("kb-2", "doc-2", 0, "内容2", embeddingProvider.embed("测试2"), 5);

            List<RetrievedChunk> results = store.search("kb-1", embeddingProvider.embed("测试1"), 10);
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getKnowledgeBaseId()).isEqualTo("kb-1");
        }

        @Test
        @DisplayName("topK 限制返回数量")
        void topKShouldLimitResultCount() {
            for (int i = 0; i < 5; i++) {
                store.store("kb-1", "doc-1", i, "内容" + i,
                        embeddingProvider.embed("内容" + i), 5);
            }

            List<RetrievedChunk> results = store.search("kb-1", embeddingProvider.embed("内容0"), 3);
            assertThat(results).hasSize(3);
        }
    }

    @Nested
    @DisplayName("delete 删除")
    class DeleteTest {

        @Test
        @DisplayName("按文档删除返回删除数量")
        void deleteByDocumentShouldReturnCount() {
            store.store("kb-1", "doc-1", 0, "内容1", embeddingProvider.embed("a"), 5);
            store.store("kb-1", "doc-1", 1, "内容2", embeddingProvider.embed("b"), 5);
            store.store("kb-1", "doc-2", 0, "内容3", embeddingProvider.embed("c"), 5);

            int count = store.deleteByDocument("doc-1");
            assertThat(count).isEqualTo(2);
            assertThat(store.size()).isEqualTo(1);
        }

        @Test
        @DisplayName("按知识库删除返回删除数量")
        void deleteByKnowledgeBaseShouldReturnCount() {
            store.store("kb-1", "doc-1", 0, "内容1", embeddingProvider.embed("a"), 5);
            store.store("kb-1", "doc-2", 0, "内容2", embeddingProvider.embed("b"), 5);
            store.store("kb-2", "doc-3", 0, "内容3", embeddingProvider.embed("c"), 5);

            int count = store.deleteByKnowledgeBase("kb-1");
            assertThat(count).isEqualTo(2);
            assertThat(store.size()).isEqualTo(1);
        }

        @Test
        @DisplayName("删除不存在的文档返回 0")
        void deleteNonExistentShouldReturn0() {
            store.store("kb-1", "doc-1", 0, "内容", embeddingProvider.embed("a"), 5);
            int count = store.deleteByDocument("non-existent");
            assertThat(count).isZero();
        }
    }

    @Nested
    @DisplayName("count 统计")
    class CountTest {

        @Test
        @DisplayName("按知识库统计分块数")
        void shouldCountByKnowledgeBase() {
            store.store("kb-1", "doc-1", 0, "内容1", embeddingProvider.embed("a"), 5);
            store.store("kb-1", "doc-2", 0, "内容2", embeddingProvider.embed("b"), 5);
            store.store("kb-2", "doc-3", 0, "内容3", embeddingProvider.embed("c"), 5);

            assertThat(store.countByKnowledgeBase("kb-1")).isEqualTo(2);
            assertThat(store.countByKnowledgeBase("kb-2")).isEqualTo(1);
            assertThat(store.countByKnowledgeBase("kb-3")).isZero();
        }
    }

    @Nested
    @DisplayName("clear 清空")
    class ClearTest {

        @Test
        @DisplayName("清空后 size 为 0")
        void clearShouldResetSize() {
            store.store("kb-1", "doc-1", 0, "内容", embeddingProvider.embed("a"), 5);
            assertThat(store.size()).isEqualTo(1);

            store.clear();
            assertThat(store.size()).isZero();
        }
    }
}
