package com.njydsz.agent.domain.model.a2a;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.njydsz.agent.domain.model.a2a.A2aPart.A2aPartType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link A2aPart} 单元测试。
 *
 * <p>覆盖：Lombok 构建器与 getter/setter、{@link A2aPartType} 大小写不敏感解析、未知/null 兜底为 TEXT。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("A2aPart A2A 消息段落模型")
class A2aPartTest {

  @Nested
  @DisplayName("模型字段")
  class ModelTest {

    @Test
    @DisplayName("Builder 应正确构建全部字段")
    void shouldBuildWithAllFields() {
      Map<String, Object> metadata = new HashMap<>();
      metadata.put("k", "v");
      A2aPart part = A2aPart.builder()
          .type(A2aPartType.TEXT)
          .text("hello")
          .data(42)
          .metadata(metadata)
          .build();

      assertThat(part.getType()).isEqualTo(A2aPartType.TEXT);
      assertThat(part.getText()).isEqualTo("hello");
      assertThat(part.getData()).isEqualTo(42);
      assertThat(part.getMetadata()).containsEntry("k", "v");
    }

    @Test
    @DisplayName("无参构造 + setter 应可用")
    void shouldSupportNoArgsConstructor() {
      A2aPart part = new A2aPart();
      part.setType(A2aPartType.DATA);
      part.setData("payload");
      assertThat(part.getType()).isEqualTo(A2aPartType.DATA);
      assertThat(part.getData()).isEqualTo("payload");
    }

    @Test
    @DisplayName("equals/hashCode 应基于字段值")
    void shouldImplementEqualsAndHashCode() {
      A2aPart a = A2aPart.builder().type(A2aPartType.TEXT).text("x").build();
      A2aPart b = A2aPart.builder().type(A2aPartType.TEXT).text("x").build();
      A2aPart c = A2aPart.builder().type(A2aPartType.FILE).text("x").build();

      assertThat(a).isEqualTo(b);
      assertThat(a.hashCode()).isEqualTo(b.hashCode());
      assertThat(a).isNotEqualTo(c);
    }
  }

  @Nested
  @DisplayName("段落类型解析")
  class PartTypeTest {

    @ParameterizedTest(name = "fromProtocolValue(\"{0}\") 应返回 {1}")
    @CsvSource({
      "text, TEXT",
      "file, FILE",
      "data, DATA",
      "TEXT, TEXT",
      "File, FILE",
      "DaTa, DATA",
    })
    @DisplayName("大小写不敏感解析应成功")
    void shouldParseCaseInsensitive(String value, A2aPartType expected) {
      assertThat(A2aPartType.fromProtocolValue(value)).isEqualTo(expected);
    }

    @Test
    @DisplayName("null 应兜底为 TEXT")
    void shouldFallbackForNull() {
      assertThat(A2aPartType.fromProtocolValue(null)).isEqualTo(A2aPartType.TEXT);
    }

    @ParameterizedTest(name = "fromProtocolValue(\"{0}\") 应兜底为 TEXT")
    @ValueSource(strings = {"image", "audio", ""})
    @DisplayName("未知值应兜底为 TEXT")
    void shouldFallbackForUnknown(String value) {
      assertThat(A2aPartType.fromProtocolValue(value)).isEqualTo(A2aPartType.TEXT);
    }

    @Test
    @DisplayName("protocolValue 应小写")
    void shouldHaveLowercaseProtocolValue() {
      for (A2aPartType type : A2aPartType.values()) {
        assertThat(type.getProtocolValue()).isEqualTo(type.getProtocolValue().toLowerCase());
      }
    }
  }
}
