package com.njydsz.agent.domain.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * {@link TextChunk} 单元测试。
 *
 * <p>覆盖嵌入状态判定、不可变复制、元数据防御性拷贝、toString 格式等值对象核心行为。
 *
 * @author ydsz-team
 * @since 26.09.16
 */
@Tag("unit")
@DisplayName("TextChunk 文本块值对象测试")
class TextChunkTest {

  /** 测试用文档 ID。 */
  private static final String DOC_ID = "doc-001";

  /** 测试用元数据。 */
  private static final Map<String, Object> TEST_METADATA;

  static {
    TEST_METADATA = new HashMap<>();
    TEST_METADATA.put("page", 1);
    TEST_METADATA.put("section", "intro");
  }

  /**
   * 构造标准测试用 TextChunk。
   *
   * @param embedding 嵌入向量（可为 null）
   * @return 构造后的 TextChunk
   */
  private TextChunk buildChunk(List<Float> embedding) {
    return new TextChunk(
        "chunk-001",
        "这是段落文本内容",
        DOC_ID,
        "测试文档",
        "nextwiki",
        0,
        100,
        TEST_METADATA,
        embedding);
  }

  @Nested
  @DisplayName("hasEmbedding 嵌入状态判定")
  class HasEmbeddingTest {

    /**
     * 未传入 embedding（null）时，hasEmbedding 应返回 false。
     */
    @Test
    @DisplayName("embedding 为 null 时 hasEmbedding 返回 false")
    void nullEmbeddingShouldReturnFalse() {
      TextChunk chunk = buildChunk(null);

      assertThat(chunk.hasEmbedding()).isFalse();
    }

    /**
     * 传入空列表时，hasEmbedding 应返回 false。
     */
    @Test
    @DisplayName("embedding 为空列表时 hasEmbedding 返回 false")
    void emptyEmbeddingShouldReturnFalse() {
      TextChunk chunk = buildChunk(List.of());

      assertThat(chunk.hasEmbedding()).isFalse();
    }

    /**
     * 传入非空向量时，hasEmbedding 应返回 true。
     */
    @Test
    @DisplayName("embedding 非空时 hasEmbedding 返回 true")
    void nonEmptyEmbeddingShouldReturnTrue() {
      TextChunk chunk = buildChunk(List.of(0.1f, 0.2f, 0.3f));

      assertThat(chunk.hasEmbedding()).isTrue();
    }
  }

  @Nested
  @DisplayName("withEmbedding 不可变复制")
  class WithEmbeddingTest {

    /**
     * withEmbedding 应返回携带新嵌入的新实例。
     */
    @Test
    @DisplayName("withEmbedding 应返回携带新嵌入的新实例")
    void withEmbeddingShouldReturnNewInstance() {
      TextChunk original = buildChunk(null);
      List<Float> embedding = List.of(0.5f, 0.6f, 0.7f);

      TextChunk embedded = original.withEmbedding(embedding);

      assertThat(embedded).isNotSameAs(original);
      assertThat(embedded.hasEmbedding()).isTrue();
    }

    /**
     * 原始实例不应被修改（不可变语义）。
     */
    @Test
    @DisplayName("withEmbedding 不应修改原始实例")
    void withEmbeddingShouldNotMutateOriginal() {
      TextChunk original = buildChunk(null);
      List<Float> embedding = List.of(0.5f, 0.6f, 0.7f);

      original.withEmbedding(embedding);

      assertThat(original.hasEmbedding()).isFalse();
    }

    /**
     * 新实例除 embedding 外其余字段应与原实例一致。
     */
    @Test
    @DisplayName("withEmbedding 新实例应保持其他字段不变")
    void withEmbeddingShouldPreserveOtherFields() {
      TextChunk original = buildChunk(null);
      List<Float> embedding = List.of(0.5f, 0.6f);

      TextChunk embedded = original.withEmbedding(embedding);

      assertThat(embedded.getId()).isEqualTo(original.getId());
      assertThat(embedded.getContent()).isEqualTo(original.getContent());
      assertThat(embedded.getDocumentId()).isEqualTo(original.getDocumentId());
      assertThat(embedded.getDocumentTitle()).isEqualTo(original.getDocumentTitle());
      assertThat(embedded.getChunkIndex()).isEqualTo(original.getChunkIndex());
    }
  }

  @Nested
  @DisplayName("元数据防御性拷贝")
  class MetadataDefensiveCopyTest {

    /**
     * 构造后修改外部 metadata 映射不应影响 TextChunk 内部的不可变视图。
     */
    @Test
    @DisplayName("修改外部 Map 不应影响 TextChunk 内部元数据")
    void mutatingExternalMapShouldNotAffectChunk() {
      Map<String, Object> externalMap = new HashMap<>();
      externalMap.put("key1", "value1");

      TextChunk chunk = new TextChunk(
          "chunk-002", "content", DOC_ID, "title",
          "source", 0, 10, externalMap, null);

      // 构造后修改外部 Map
      externalMap.put("key2", "value2");
      externalMap.put("key1", "mutated");

      // TextChunk 内部不可变视图应不受影响
      Map<String, Object> chunkMetadata = chunk.getMetadata();
      assertThat(chunkMetadata).containsEntry("key1", "value1");
      assertThat(chunkMetadata).doesNotContainKey("key2");
    }

    /**
     * getMetadata 返回的 Map 应不可修改（UnsupportedOperationException）。
     */
    @Test
    @DisplayName("getMetadata 返回的 Map 应不可修改")
    void getMetadataShouldReturnUnmodifiableMap() {
      TextChunk chunk = buildChunk(null);

      Map<String, Object> metadata = chunk.getMetadata();

      assertThatThrownBy(() -> chunk.getMetadata().put("hack", "value"))
          .isInstanceOf(UnsupportedOperationException.class);
    }
  }

  @Nested
  @DisplayName("toString 格式验证")
  class ToStringTest {

    /**
     * toString 应包含关键信息：id、docId、chunkIndex、hasEmbedding。
     */
    @Test
    @DisplayName("toString 应包含 id、docIndex、tokens、embedded 信息")
    void toStringShouldContainKeyInfo() {
      TextChunk chunk = buildChunk(List.of(0.1f, 0.2f));

      String str = chunk.toString();

      assertThat(str).contains("chunk-001").contains(DOC_ID).contains("embedded=true");
    }

    /**
     * 未嵌入时 toString 应反映 embedded=false。
     */
    @Test
    @DisplayName("未嵌入时 toString 应显示 embedded=false")
    void toStringShouldShowNotEmbedded() {
      TextChunk chunk = buildChunk(null);

      assertThat(chunk.toString()).contains("embedded=false");
    }
  }

  @Nested
  @DisplayName("null 安全防御")
  class NullSafetyTest {

    /**
     * metadata 为 null 时应内部转为空 Map，不抛出异常。
     */
    @Test
    @DisplayName("metadata 为 null 时应内部转为空 Map")
    void nullMetadataShouldResultInEmptyMap() {
      TextChunk chunk = new TextChunk(
          "chunk-003", "content", DOC_ID, "title",
          "source", 0, 10, null, null);

      assertThat(chunk.getMetadata()).isNotNull().isEmpty();
    }

    /**
     * id 为 null 应抛出 NullPointerException（Objects.requireNonNull）。
     */
    @Test
    @DisplayName("id 为 null 应抛出 NullPointerException")
    void nullIdShouldThrowNullPointerException() {
      assertThatThrownBy(() -> new TextChunk(
          null, "content", DOC_ID, "title",
          "source", 0, 10, null, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("id");
    }
  }
}
