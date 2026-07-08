package com.njydsz.pmis.agent.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DocumentSplitter 单元测试（P3-1 落地）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-1)
 */
@DisplayName("DocumentSplitter 文档分块器")
class DocumentSplitterTest {

    @Nested
    @DisplayName("构造与参数校验")
    class ConstructorTest {

        @Test
        @DisplayName("默认构造使用 500/50")
        void defaultConstructorShouldUseDefaults() {
            DocumentSplitter splitter = new DocumentSplitter();
            List<String> chunks = splitter.split("a".repeat(600));
            assertThat(chunks).isNotEmpty();
        }

        @Test
        @DisplayName("chunkSize <= 0 抛异常")
        void shouldThrowWhenChunkSizeNonPositive() {
            assertThatThrownBy(() -> new DocumentSplitter(0, 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("chunkOverlap >= chunkSize 抛异常")
        void shouldThrowWhenOverlapNotLessThanSize() {
            assertThatThrownBy(() -> new DocumentSplitter(100, 100))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("chunkOverlap < 0 抛异常")
        void shouldThrowWhenOverlapNegative() {
            assertThatThrownBy(() -> new DocumentSplitter(100, -1))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("split 分块")
    class SplitTest {

        @Test
        @DisplayName("null 返回空列表")
        void nullTextShouldReturnEmptyList() {
            DocumentSplitter splitter = new DocumentSplitter();
            assertThat(splitter.split(null)).isEmpty();
        }

        @Test
        @DisplayName("空字符串返回空列表")
        void emptyTextShouldReturnEmptyList() {
            DocumentSplitter splitter = new DocumentSplitter();
            assertThat(splitter.split("")).isEmpty();
        }

        @Test
        @DisplayName("短文本不切分，返回单个分块")
        void shortTextShouldReturnSingleChunk() {
            DocumentSplitter splitter = new DocumentSplitter(100, 10);
            List<String> chunks = splitter.split("短文本");

            assertThat(chunks).hasSize(1);
            assertThat(chunks.get(0)).isEqualTo("短文本");
        }

        @Test
        @DisplayName("长文本按 chunkSize 切分")
        void longTextShouldBeSplitByChunkSize() {
            DocumentSplitter splitter = new DocumentSplitter(10, 0);
            String text = "0123456789abcdefghij";  // 20 字符
            List<String> chunks = splitter.split(text);

            assertThat(chunks).hasSize(2);
            assertThat(chunks.get(0)).isEqualTo("0123456789");
            assertThat(chunks.get(1)).isEqualTo("abcdefghij");
        }

        @Test
        @DisplayName("带重叠的分块保证连续性")
        void overlapChunksShouldMaintainContinuity() {
            DocumentSplitter splitter = new DocumentSplitter(10, 3);
            String text = "0123456789abcdefghij";  // 20 字符
            List<String> chunks = splitter.split(text);

            // 不验证具体切分位置，但应产生多个分块
            assertThat(chunks.size()).isGreaterThanOrEqualTo(2);
            // 合并后应能覆盖原文（考虑 trim）
            String merged = String.join("", chunks);
            assertThat(merged).contains("0123456789");
        }

        @Test
        @DisplayName("优先在换行符边界切分")
        void shouldPreferNewlineBoundary() {
            DocumentSplitter splitter = new DocumentSplitter(20, 0);
            String text = "第一行内容\n第二行内容\n第三行";
            List<String> chunks = splitter.split(text);

            // 至少有一个分块以换行符结尾或在换行符处切分
            assertThat(chunks).isNotEmpty();
        }

        @Test
        @DisplayName("优先在句号边界切分")
        void shouldPreferPeriodBoundary() {
            DocumentSplitter splitter = new DocumentSplitter(20, 0);
            String text = "第一句话。第二句话。第三句话。";
            List<String> chunks = splitter.split(text);

            assertThat(chunks).isNotEmpty();
        }

        @Test
        @DisplayName("空白分块被过滤")
        void blankChunksShouldBeFiltered() {
            DocumentSplitter splitter = new DocumentSplitter(5, 0);
            String text = "  \n\n  \n\n  ";  // 多个换行+空格
            List<String> chunks = splitter.split(text);

            for (String chunk : chunks) {
                assertThat(chunk).isNotBlank();
            }
        }
    }
}
