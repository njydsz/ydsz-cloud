package com.njydsz.common.docs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.njydsz.common.docs.config.DocsProperties;
import com.njydsz.common.docs.convert.DocumentConverter;
import com.njydsz.common.docs.domain.DocumentContent;
import com.njydsz.common.docs.domain.DocumentParseResult;
import com.njydsz.common.docs.domain.DocumentSection;
import com.njydsz.common.docs.domain.ParseOptions;
import com.njydsz.common.docs.enums.DocumentFormat;
import com.njydsz.common.docs.metrics.DocsMetrics;
import com.njydsz.common.docs.ocr.OcrProvider;
import com.njydsz.common.docs.parser.DocumentParser;
import com.njydsz.common.docs.parser.registry.DocumentParserRegistry;
import com.njydsz.common.docs.pipeline.DocumentProcessorPipeline;
import com.njydsz.common.docs.preprocess.pipeline.PreprocessPipeline;
import com.njydsz.common.util.io.TempFileManager;

/**
 * {@link DocumentService} 单元测试 —— 覆盖解析、预处理、转换、PII、安全的公开路径与分支条件。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@DisplayName("DocumentService 测试")
class DocumentServiceTest {

  private DocumentParserRegistry parserRegistry;
  private PreprocessPipeline preprocessPipeline;
  private DocsProperties properties;
  private DocumentConverter converter;
  private OcrProvider ocrProvider;
  private DocsMetrics metrics;
  private TempFileManager tempFileManager;
  private ObjectProvider<DocumentConverter> converterProvider;
  private ObjectProvider<OcrProvider> ocrProviderProvider;
  private ObjectProvider<DocsMetrics> metricsProvider;
  private DocumentProcessorPipeline processorPipeline;
  private DocumentService service;

  @BeforeEach
  void setUp() {
    parserRegistry = mock(DocumentParserRegistry.class);
    preprocessPipeline = mock(PreprocessPipeline.class);
    properties = new DocsProperties();
    converter = mock(DocumentConverter.class);
    ocrProvider = mock(OcrProvider.class);
    metrics = mock(DocsMetrics.class);
    tempFileManager = mock(TempFileManager.class);
    processorPipeline = mock(DocumentProcessorPipeline.class);
    converterProvider = mock(ObjectProvider.class);
    ocrProviderProvider = mock(ObjectProvider.class);
    metricsProvider = mock(ObjectProvider.class);
    when(converterProvider.getIfAvailable()).thenReturn(converter);
    when(ocrProviderProvider.getIfAvailable()).thenReturn(ocrProvider);
    when(metricsProvider.getIfAvailable()).thenReturn(metrics);

    service =
        new DocumentService(
            parserRegistry,
            preprocessPipeline,
            List.of(),
            List.of(),
            properties,
            converterProvider,
            ocrProviderProvider,
            metricsProvider,
            tempFileManager,
            processorPipeline);
  }

  @Nested
  @DisplayName("场景：解析文档")
  class WhenParsing {

    @Test
    @DisplayName("能识别的 TXT 格式应成功解析")
    void shouldParseRecognizedFormat() throws Exception {
      InputStream stream = new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8));
      DocumentParser mockParser = mock(DocumentParser.class);
      DocumentContent expected =
          DocumentContent.builder()
              .text("hello")
              .sections(
                  List.of(DocumentSection.builder().type("paragraph").content("hello").build()))
              .totalChars(5)
              .totalPages(1)
              .build();
      when(parserRegistry.isSupported(DocumentFormat.TXT)).thenReturn(true);
      when(parserRegistry.getParser(DocumentFormat.TXT)).thenReturn(mockParser);
      when(mockParser.parse(any(), eq("test.txt"), any())).thenReturn(expected);

      DocumentParseResult result =
          service.parse(stream, "test.txt", ParseOptions.builder().build());

      assertThat(result.isSuccess()).isTrue();
      assertThat(result.getContent()).isNotNull();
      assertThat(result.getContent().getText()).isEqualTo("hello");
      verify(metrics).recordParse(eq(DocumentFormat.TXT), eq(true), any(long.class));
    }

    @Test
    @DisplayName("未知格式应返回失败的结果（不抛异常）")
    void shouldReturnFailureForUnsupportedFormat() {
      InputStream stream = new ByteArrayInputStream(new byte[0]);

      DocumentParseResult result =
          service.parse(stream, "unknown.xyz", ParseOptions.builder().build());

      assertThat(result.isSuccess()).isFalse();
      assertThat(result.getErrorMessage()).isNotBlank();
    }

    @Test
    @DisplayName("解析异常时应返回失败结果而不抛出")
    void shouldReturnFailureOnException() {
      InputStream stream = new ByteArrayInputStream("data".getBytes(StandardCharsets.UTF_8));
      when(parserRegistry.isSupported(DocumentFormat.TXT)).thenReturn(true);
      when(parserRegistry.getParser(DocumentFormat.TXT))
          .thenThrow(new RuntimeException("simulated"));

      DocumentParseResult result =
          service.parse(stream, "test.txt", ParseOptions.builder().build());

      assertThat(result.isSuccess()).isFalse();
      assertThat(result.getErrorMessage()).contains("simulated");
    }
  }

  @Nested
  @DisplayName("场景：预处理")
  class WhenPreprocessing {

    @Test
    @DisplayName("预处理启用时应调用 pipeline")
    void shouldInvokePipeline() {
      properties.setPreprocessEnabled(true);
      DocumentContent input = DocumentContent.builder().text("raw").build();
      DocumentContent processed = DocumentContent.builder().text("cleaned").build();
      when(preprocessPipeline.execute(input)).thenReturn(processed);

      DocumentContent result = service.preprocess(input);

      assertThat(result.getText()).isEqualTo("cleaned");
    }

    @Test
    @DisplayName("预处理禁用时应直接返回原内容")
    void shouldReturnOriginalWhenDisabled() {
      properties.setPreprocessEnabled(false);
      DocumentContent input = DocumentContent.builder().text("raw").build();

      DocumentContent result = service.preprocess(input);

      assertThat(result).isSameAs(input);
      verifyNoInteractions(preprocessPipeline);
    }
  }

  @Nested
  @DisplayName("场景：格式转换")
  class WhenConverting {

    @Test
    @DisplayName("支持转换时应委托给 converter")
    void shouldDelegateToConverter() {
      InputStream stream = new ByteArrayInputStream(new byte[0]);
      byte[] expected = "converted".getBytes(StandardCharsets.UTF_8);
      when(converter.convert(any(), eq("test.docx"), any(), any())).thenReturn(expected);

      byte[] result = service.convert(stream, "test.docx", DocumentFormat.TXT);

      assertThat(result).isEqualTo(expected);
    }
  }

  @Nested
  @DisplayName("场景：空内容安全")
  class WhenEmptyContent {

    @Test
    @DisplayName("parseAndPreprocess 成功但内容 null 时不应崩溃")
    void shouldNotPreprocessWhenContentIsNull() {
      DocumentParseResult parseResult =
          DocumentParseResult.builder()
              .isSuccess(true)
              .content(null)
              .fileName("safe.txt")
              .build();
      // 直接使用 service.parseAndPreprocess 时需模拟 parse 调用
      InputStream stream = new ByteArrayInputStream(new byte[0]);
      DocumentParser mockParser = mock(DocumentParser.class);
      DocumentContent contentWithNull = DocumentContent.builder().text(null).build();
      when(parserRegistry.isSupported(DocumentFormat.TXT)).thenReturn(true);
      when(parserRegistry.getParser(DocumentFormat.TXT)).thenReturn(mockParser);
      when(mockParser.parse(any(), any(), any())).thenReturn(contentWithNull);

      // 不抛异常即通过
      service.parseAndPreprocess(stream, "test.txt", ParseOptions.builder().build());
    }
  }
}
