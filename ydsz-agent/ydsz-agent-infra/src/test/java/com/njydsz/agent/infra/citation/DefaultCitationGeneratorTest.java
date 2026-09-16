package com.njydsz.agent.infra.citation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.njydsz.agent.domain.citation.Citation;
import com.njydsz.agent.domain.rag.TextChunk;

/**
 * {@link DefaultCitationGenerator} 单元测试。
 *
 * <p>覆盖空输入防御、单个/批量转换、元数据缺失降级、超长内容截断、得分安全提取等核心逻辑。
 *
 * @author ydsz-team
 * @since 26.09.16
 */
@Tag("unit")
@DisplayName("DefaultCitationGenerator 引用生成测试")
class DefaultCitationGeneratorTest {

  /** 最大摘录长度常量（与被测类一致）。 */
  private static final int MAX_EXCERPT_LENGTH = 200;

  /** 默认测试用文档 ID。 */
  private static final String TEST_DOC_ID = "doc-test-001";

  /** 默认测试用文档标题。 */
  private static final String TEST_DOC_TITLE = "测试文档";

  /** 默认测试用 source path。 */
  private static final String TEST_SOURCE_PATH = "/wiki/page/test";

  /** 被测生成器。 */
  private DefaultCitationGenerator generator;

  @BeforeEach
  void setUp() {
    generator = new DefaultCitationGenerator();
  }

  /**
   * 构造单个标准 TextChunk（含完整元数据）。
   *
   * @param content 文本内容
   * @param score   元数据中的相似度得分
   * @return 构造后的 TextChunk 实例
   */
  private TextChunk buildChunk(String content, double score) {
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("sourcePath", TEST_SOURCE_PATH);
    metadata.put("score", score);
    return new TextChunk(
        "chunk-001",
        content,
        TEST_DOC_ID,
        TEST_DOC_TITLE,
        "nextwiki",
        0,
        content.length(),
        metadata,
        null);
  }

  @Nested
  @DisplayName("generateCitations 批量生成")
  class GenerateCitations {

    /**
     * null 输入应返回空列表，不抛出异常。
     */
    @Test
    @DisplayName("null 输入应返回空列表")
    void nullInputShouldReturnEmptyList() {
      List<Citation> result = generator.generateCitations(null);

      assertThat(result).isNotNull().isEmpty();
    }

    /**
     * 空列表输入应返回空列表。
     */
    @Test
    @DisplayName("空列表应返回空列表")
    void emptyListShouldReturnEmptyList() {
      List<Citation> result = generator.generateCitations(List.of());

      assertThat(result).isNotNull().isEmpty();
    }

    /**
     * 单个 Chunk 应生成单个 Citation。
     */
    @Test
    @DisplayName("单个 Chunk 应生成对应 Citation")
    void singleChunkShouldGenerateSingleCitation() {
      TextChunk chunk = buildChunk("短文本", 0.95);

      List<Citation> result = generator.generateCitations(List.of(chunk));

      assertThat(result).hasSize(1);
      Citation citation = result.get(0);
      assertThat(citation.documentId()).isEqualTo(TEST_DOC_ID);
      assertThat(citation.documentTitle()).isEqualTo(TEST_DOC_TITLE);
      assertThat(citation.sourcePath()).isEqualTo(TEST_SOURCE_PATH);
      assertThat(citation.score()).isEqualTo(0.95);
    }

    /**
     * 多个 Chunk 应按序生成同等数量 Citation。
     */
    @Test
    @DisplayName("多个 Chunk 应生成同等数量 Citation")
    void multipleChunksShouldGenerateEqualCitations() {
      TextChunk chunk1 = buildChunk("第一个片段", 0.9);
      TextChunk chunk2 = buildChunk("第二个片段", 0.8);
      TextChunk chunk3 = buildChunk("第三个片段", 0.7);

      List<Citation> result = generator.generateCitations(List.of(chunk1, chunk2, chunk3));

      assertThat(result).hasSize(3);
      assertThat(result.get(0).score()).isEqualTo(0.9);
      assertThat(result.get(1).score()).isEqualTo(0.8);
      assertThat(result.get(2).score()).isEqualTo(0.7);
    }
  }

  @Nested
  @DisplayName("generateCitation 单个生成边界场景")
  class GenerateCitation {

    /**
     * null Chunk 输入应返回 null。
     */
    @Test
    @DisplayName("null Chunk 应返回 null")
    void nullChunkShouldReturnNull() {
      Citation result = generator.generateCitation(null);

      assertThat(result).isNull();
    }

    /**
     * 不含 sourcePath 元数据时，sourcePath 应为空字符串。
     */
    @Test
    @DisplayName("元数据缺失 sourcePath 时应降级为空字符串")
    void missingSourcePathShouldDefaultToEmpty() {
      TextChunk chunk = new TextChunk(
          "chunk-002",
          "无来源路径的文本",
          TEST_DOC_ID,
          TEST_DOC_TITLE,
          "nextwiki",
          1,
          10,
          Map.of("score", 0.85),
          null);

      Citation result = generator.generateCitation(chunk);

      assertThat(result.sourcePath()).isEmpty();
    }

    /**
     * 不含 score 元数据时，score 应为 0.0。
     */
    @Test
    @DisplayName("元数据缺失 score 时应降级为 0.0")
    void missingScoreShouldDefaultToZero() {
      TextChunk chunk = new TextChunk(
          "chunk-003",
          "无得分的文本",
          TEST_DOC_ID,
          TEST_DOC_TITLE,
          "nextwiki",
          2,
          10,
          Map.of("sourcePath", TEST_SOURCE_PATH),
          null);

      Citation result = generator.generateCitation(chunk);

      assertThat(result.score()).isEqualTo(0.0);
    }

    /**
     * 超长内容应被截断至 200 字符并追加 "..."。
     */
    @Test
    @DisplayName("超长内容应截断至 200 字符并追加省略号")
    void longContentShouldTruncateWithEllipsis() {
      String longContent = "A".repeat(MAX_EXCERPT_LENGTH + 50);
      TextChunk chunk = buildChunk(longContent, 0.5);

      Citation result = generator.generateCitation(chunk);

      assertThat(result.textExcerpt()).hasSize(MAX_EXCERPT_LENGTH + 3);
      assertThat(result.textExcerpt()).endsWith("...");
    }

    /**
     * 不超过 200 字符的内容不应被截断。
     */
    @Test
    @DisplayName("短内容应保持完整不被截断")
    void shortContentShouldNotTruncate() {
      String shortContent = "这是一段短文本";
      TextChunk chunk = buildChunk(shortContent, 0.5);

      Citation result = generator.generateCitation(chunk);

      assertThat(result.textExcerpt()).isEqualTo(shortContent);
      assertThat(result.textExcerpt()).doesNotEndWith("...");
    }

    /**
     * score 为字符串数字时应正确解析为 double。
     */
    @Test
    @DisplayName("字符串类型的 score 应正确解析")
    void stringScoreShouldParseToDouble() {
      Map<String, Object> metadata = new HashMap<>();
      metadata.put("sourcePath", TEST_SOURCE_PATH);
      metadata.put("score", "0.75");
      TextChunk chunk = new TextChunk(
          "chunk-004", "文本", TEST_DOC_ID, TEST_DOC_TITLE,
          "nextwiki", 3, 10, metadata, null);

      Citation result = generator.generateCitation(chunk);

      assertThat(result.score()).isEqualTo(0.75);
    }
  }
}
