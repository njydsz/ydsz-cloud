package com.njydsz.pmis.agent.engine.memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TokenCounter 单元测试（P1-3 落地）
 *
 * <p>覆盖：
 * <ul>
 *   <li>null / 空字符串 / 纯空白 → 0</li>
 *   <li>纯英文文本估算（单词数 × 1.3）</li>
 *   <li>纯中文文本估算（字符数 × 1.5）</li>
 *   <li>中英混合文本估算</li>
 *   <li>纯数字 / 符号估算</li>
 *   <li>结果向上取整</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P1-3)
 */
@DisplayName("TokenCounter token 计数测试")
class TokenCounterTest {

    // ==================== 边界值测试 ====================

    @Nested
    @DisplayName("边界值测试")
    class BoundaryTest {

        @Test
        @DisplayName("null 输入返回 0")
        void shouldReturnZeroForNull() {
            assertThat(TokenCounter.estimate(null)).isEqualTo(0);
        }

        @Test
        @DisplayName("空字符串返回 0")
        void shouldReturnZeroForEmpty() {
            assertThat(TokenCounter.estimate("")).isEqualTo(0);
        }

        @Test
        @DisplayName("纯空白字符返回 0")
        void shouldReturnZeroForWhitespace() {
            assertThat(TokenCounter.estimate("   ")).isEqualTo(0);
            assertThat(TokenCounter.estimate("\t\n\r")).isEqualTo(0);
        }
    }

    // ==================== 纯英文测试 ====================

    @Nested
    @DisplayName("纯英文文本测试")
    class EnglishTextTest {

        @Test
        @DisplayName("单词数 × 1.3 向上取整")
        void shouldEstimateByWordCount() {
            // "hello world" = 2 单词 → 2 * 1.3 = 2.6 → 向上取整 = 3
            assertThat(TokenCounter.estimate("hello world")).isEqualTo(3);
        }

        @Test
        @DisplayName("单个英文单词")
        void shouldEstimateSingleWord() {
            // "hello" = 1 单词 → 1 * 1.3 = 1.3 → 向上取整 = 2
            assertThat(TokenCounter.estimate("hello")).isEqualTo(2);
        }

        @Test
        @DisplayName("长英文文本 token 数应大于单词数")
        void shouldReturnMoreThanWordCount() {
            String text = "the quick brown fox jumps over the lazy dog";
            int words = text.split("\\s+").length;
            int tokens = TokenCounter.estimate(text);
            assertThat(tokens).isGreaterThan(words);
        }
    }

    // ==================== 纯中文测试 ====================

    @Nested
    @DisplayName("纯中文文本测试")
    class ChineseTextTest {

        @Test
        @DisplayName("中文字符数 × 1.5 向上取整")
        void shouldEstimateByCharCount() {
            // "你好" = 2 字符 → 2 * 1.5 = 3.0 → 向上取整 = 3
            assertThat(TokenCounter.estimate("你好")).isEqualTo(3);
        }

        @Test
        @DisplayName("单个中文字符")
        void shouldEstimateSingleChar() {
            // "中" = 1 字符 → 1 * 1.5 = 1.5 → 向上取整 = 2
            assertThat(TokenCounter.estimate("中")).isEqualTo(2);
        }

        @Test
        @DisplayName("中文长文本 token 数应大于字符数的一半")
        void shouldReturnReasonableForLongText() {
            String text = "今天天气真好，适合出去散步。";
            int chars = text.length();
            int tokens = TokenCounter.estimate(text);
            assertThat(tokens).isGreaterThan(chars / 2);
        }
    }

    // ==================== 混合文本测试 ====================

    @Nested
    @DisplayName("中英混合文本测试")
    class MixedTextTest {

        @Test
        @DisplayName("中英混合文本 token 数应大于纯中文或纯英文")
        void shouldEstimateMixedText() {
            String text = "Hello 你好 World 世界";
            int tokens = TokenCounter.estimate(text);
            assertThat(tokens).isGreaterThan(4);
        }

        @Test
        @DisplayName("中文 + 标点 + 英文")
        void shouldHandleChinesePunctuationEnglish() {
            String text = "你好，hello！世界。";
            int tokens = TokenCounter.estimate(text);
            assertThat(tokens).isGreaterThan(0);
        }
    }

    // ==================== 数字与符号测试 ====================

    @Nested
    @DisplayName("数字与符号测试")
    class NumberAndSymbolTest {

        @Test
        @DisplayName("纯数字按单词计算")
        void shouldEstimateNumbersAsWord() {
            // "123 456" = 2 单词 → 2 * 1.3 = 2.6 → 向上取整 = 3
            assertThat(TokenCounter.estimate("123 456")).isEqualTo(3);
        }

        @Test
        @DisplayName("符号按字符数 1:1 估算")
        void shouldEstimateSymbolsByChar() {
            // "!!!" = 3 符号 → 3
            assertThat(TokenCounter.estimate("!!!")).isEqualTo(3);
        }
    }
}
