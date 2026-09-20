package com.njydsz.common.docs.parser.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.njydsz.common.docs.domain.DocumentContent;
import com.njydsz.common.docs.domain.ParseOptions;
import com.njydsz.common.docs.enums.DocumentFormat;
import com.njydsz.common.docs.exception.DocumentException;
import com.njydsz.common.docs.parser.DocumentParser;

/**
 * {@link DocumentParserRegistry} 单元测试。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@DisplayName("DocumentParserRegistry 测试")
class DocumentParserRegistryTest {

  private DocumentParserRegistry registry;

  @BeforeEach
  void setUp() {
    registry = new DocumentParserRegistry(List.of());
  }

  @Nested
  @DisplayName("场景：注册与查询")
  class WhenRegistering {

    @Test
    @DisplayName("注册后应能通过主格式检索到解析器")
    void shouldFindRegisteredParser() {
      DocumentParser parser = new StubParser(DocumentFormat.TXT);
      registry.register(parser);

      assertThat(registry.isSupported(DocumentFormat.TXT)).isTrue();
      assertThat(registry.getParser(DocumentFormat.TXT)).isSameAs(parser);
    }

    @Test
    @DisplayName("未注册的格式应抛出 DocumentException")
    void shouldThrowForUnregisteredFormat() {
      assertThatThrownBy(() -> registry.getParser(DocumentFormat.PDF))
          .isInstanceOf(DocumentException.class);
    }

    @Test
    @DisplayName("isSupported 对未注册格式返回 false")
    void shouldReturnFalseForUnsupportedFormat() {
      assertThat(registry.isSupported(DocumentFormat.XLSX)).isFalse();
    }
  }

  @Nested
  @DisplayName("场景：空注册表")
  class WhenEmpty {

    @Test
    @DisplayName("构造时应接受空 parsers 列表")
    void shouldAcceptEmptyList() {
      DocumentParserRegistry emptyRegistry = new DocumentParserRegistry(List.of());
      assertThat(emptyRegistry.getSupportedFormats()).isEmpty();
    }
  }

  /** 桩解析器 —— 仅返回空内容。 */
  private static class StubParser implements DocumentParser {

    private final DocumentFormat format;

    StubParser(DocumentFormat format) {
      this.format = format;
    }

    @Override
    public DocumentContent parse(InputStream inputStream, String fileName, ParseOptions options) {
      return DocumentContent.builder().text("").build();
    }

    @Override
    public DocumentFormat getSupportedFormat() {
      return format;
    }
  }
}
