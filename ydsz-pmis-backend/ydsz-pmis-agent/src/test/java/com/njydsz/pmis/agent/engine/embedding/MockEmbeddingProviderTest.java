package com.njydsz.pmis.agent.engine.embedding;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MockEmbeddingProvider 单元测试（P3-1 落地）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-1)
 */
@DisplayName("MockEmbeddingProvider Mock 向量化")
class MockEmbeddingProviderTest {

    private final MockEmbeddingProvider provider = new MockEmbeddingProvider();

    @Nested
    @DisplayName("基本属性")
    class BasicTest {

        @Test
        @DisplayName("name 返回 mock")
        void nameShouldReturnMock() {
            assertThat(provider.name()).isEqualTo("mock");
        }

        @Test
        @DisplayName("dimension 返回 8")
        void dimensionShouldReturn8() {
            assertThat(provider.dimension()).isEqualTo(MockEmbeddingProvider.DIMENSION);
            assertThat(provider.dimension()).isEqualTo(8);
        }
    }

    @Nested
    @DisplayName("embed 向量化")
    class EmbedTest {

        @Test
        @DisplayName("相同文本产生相同向量（确定性）")
        void sameTextShouldProduceSameVector() {
            float[] v1 = provider.embed("项目风险管理");
            float[] v2 = provider.embed("项目风险管理");

            assertThat(v1).hasSize(8);
            assertThat(v1).containsExactly(v2);
        }

        @Test
        @DisplayName("不同文本大概率产生不同向量")
        void differentTextShouldProduceDifferentVector() {
            float[] v1 = provider.embed("项目风险管理");
            float[] v2 = provider.embed("资源配置优化");

            assertThat(v1).isNotEqualTo(v2);
        }

        @Test
        @DisplayName("向量为归一化（L2 范数 ≈ 1）")
        void vectorShouldBeNormalized() {
            float[] v = provider.embed("测试归一化");

            double norm = 0;
            for (float f : v) {
                norm += f * f;
            }
            norm = Math.sqrt(norm);

            assertThat(norm).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-6));
        }

        @Test
        @DisplayName("空文本返回零向量")
        void emptyTextShouldReturnZeroVector() {
            float[] v = provider.embed("");
            assertThat(v).hasSize(8);
            for (float f : v) {
                assertThat(f).isZero();
            }
        }

        @Test
        @DisplayName("null 返回零向量")
        void nullTextShouldReturnZeroVector() {
            float[] v = provider.embed(null);
            assertThat(v).hasSize(8);
            for (float f : v) {
                assertThat(f).isZero();
            }
        }
    }

    @Nested
    @DisplayName("cosineSimilarity 余弦相似度")
    class CosineSimilarityTest {

        @Test
        @DisplayName("相同向量相似度为 1")
        void sameVectorShouldHaveSimilarity1() {
            float[] v = provider.embed("测试");
            double sim = MockEmbeddingProvider.cosineSimilarity(v, v);

            assertThat(sim).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-6));
        }

        @Test
        @DisplayName("零向量相似度为 0")
        void zeroVectorShouldHaveSimilarity0() {
            float[] zero = new float[8];
            float[] v = provider.embed("测试");

            double sim = MockEmbeddingProvider.cosineSimilarity(zero, v);
            assertThat(sim).isZero();
        }

        @Test
        @DisplayName("不同长度向量相似度为 0")
        void differentLengthShouldReturn0() {
            float[] a = new float[8];
            float[] b = new float[16];

            double sim = MockEmbeddingProvider.cosineSimilarity(a, b);
            assertThat(sim).isZero();
        }

        @Test
        @DisplayName("null 向量相似度为 0")
        void nullVectorShouldReturn0() {
            double sim = MockEmbeddingProvider.cosineSimilarity(null, new float[8]);
            assertThat(sim).isZero();
        }
    }

    @Nested
    @DisplayName("toPgVectorString pgvector 字符串")
    class PgVectorStringTest {

        @Test
        @DisplayName("正常向量转为 pgvector 字符串")
        void shouldConvertToPgVectorString() {
            float[] v = {1.0f, 2.0f, 3.0f};
            String str = provider.toPgVectorString(v);

            assertThat(str).startsWith("[");
            assertThat(str).endsWith("]");
            assertThat(str).contains("1.0");
            assertThat(str).contains("2.0");
            assertThat(str).contains("3.0");
        }

        @Test
        @DisplayName("空向量转为空括号")
        void emptyVectorShouldReturnEmptyBrackets() {
            String str = provider.toPgVectorString(new float[0]);
            assertThat(str).isEqualTo("[]");
        }

        @Test
        @DisplayName("null 向量转为空括号")
        void nullVectorShouldReturnEmptyBrackets() {
            String str = provider.toPgVectorString(null);
            assertThat(str).isEqualTo("[]");
        }
    }

    @Nested
    @DisplayName("normalize 归一化")
    class NormalizeTest {

        @Test
        @DisplayName("非零向量归一化后 L2 范数为 1")
        void shouldNormalizeToUnitLength() {
            float[] v = {3.0f, 4.0f};
            float[] normalized = MockEmbeddingProvider.normalize(v);

            double norm = 0;
            for (float f : normalized) {
                norm += f * f;
            }
            assertThat(Math.sqrt(norm)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-6));
        }

        @Test
        @DisplayName("零向量归一化后保持不变")
        void zeroVectorShouldRemainZero() {
            float[] zero = {0.0f, 0.0f, 0.0f};
            float[] normalized = MockEmbeddingProvider.normalize(zero);

            for (float f : normalized) {
                assertThat(f).isZero();
            }
        }
    }
}
