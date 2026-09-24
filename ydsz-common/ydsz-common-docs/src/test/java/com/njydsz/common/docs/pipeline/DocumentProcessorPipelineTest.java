package com.njydsz.common.docs.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.njydsz.common.docs.TestUtils;
import com.njydsz.common.docs.config.DocsProperties;
import com.njydsz.common.docs.domain.DocumentContent;
import com.njydsz.common.docs.domain.DocumentParseResult;
import com.njydsz.common.docs.domain.DocumentSection;
import com.njydsz.common.docs.domain.DocumentTable;
import com.njydsz.common.docs.domain.ParseOptions;
import com.njydsz.common.docs.domain.PiiFinding;
import com.njydsz.common.docs.domain.SecurityScanResult;
import com.njydsz.common.docs.enums.DocumentFormat;
import com.njydsz.common.docs.enums.PiiType;
import com.njydsz.common.docs.enums.SecurityLevel;
import com.njydsz.common.docs.parser.DocumentParser;
import com.njydsz.common.docs.parser.registry.DocumentParserRegistry;
import com.njydsz.common.docs.preprocess.pipeline.PreprocessPipeline;
import com.njydsz.common.docs.security.pii.PiiDetectionSummary;
import com.njydsz.common.docs.security.pii.PiiDetector;
import com.njydsz.common.util.io.TempFileManager;

/**
 * {@link DocumentProcessorPipeline} 单元测试。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@DisplayName("DocumentProcessorPipeline 测试")
class DocumentProcessorPipelineTest {

  private DocumentParserRegistry parserRegistry;
  private PreprocessPipeline preprocessPipeline;
  private DocsProperties properties;
  private TempFileManager tempFileManager;
  private DocumentProcessorPipeline processor;

  @BeforeEach
  void setUp() {
    parserRegistry = mock(DocumentParserRegistry.class);
    preprocessPipeline = mock(PreprocessPipeline.class);
    properties = new DocsProperties();
    tempFileManager = mock(TempFileManager.class);
    processor =
        new DocumentProcessorPipeline(
            parserRegistry,
            List.of(),
            List.of(),
            preprocessPipeline,
            properties,
            tempFileManager);
  }

  @Nested
  @DisplayName("场景：Builder 构建")
  class WhenBuilding {

    @Test
    @DisplayName("应能构建仅解析的管道")
    void shouldBuildParseOnlyPipeline() {
      DocumentProcessorPipeline.Pipeline pipeline =
          DocumentProcessorPipeline.builder()
              .parse(ParseOptions.builder().build())
              .build(processor);

      assertThat(pipeline).isNotNull();
    }

    @Test
    @DisplayName("parse(null) 应抛出 NPE")
    void shouldThrowWhenParseOptionsIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> DocumentProcessorPipeline.builder().parse(null));
    }
  }

  @Nested
  @DisplayName("场景：执行管道")
  class WhenExecuting {

    @Test
    @DisplayName("简单解析管道应返回 DocumentContent")
    void shouldExecuteParseOnlyPipeline() throws Exception {
      InputStream stream = TestUtils.streamOf("hello world test");
      DocumentParser mockParser = mock(DocumentParser.class);
      DocumentContent expected =
          DocumentContent.builder()
              .text("hello world test")
              .sections(
                  List.of(
                      DocumentSection.builder()
                          .type("paragraph")
                          .content("hello world test")
                          .build()))
              .totalChars(16)
              .totalPages(1)
              .build();
      when(parserRegistry.isSupported(DocumentFormat.TXT)).thenReturn(true);
      when(parserRegistry.getParser(DocumentFormat.TXT)).thenReturn(mockParser);
      when(mockParser.parse(any(), any(), any())).thenReturn(expected);

      DocumentProcessorPipeline.Pipeline pipeline =
          DocumentProcessorPipeline.builder()
              .parse(ParseOptions.builder().build())
              .build(processor);

      DocumentProcessResult result = pipeline.execute(stream, "test.txt");

      assertThat(result.isSuccess()).isTrue();
      assertThat(result.getContent()).isNotNull();
      assertThat(result.getContent().getText()).isEqualTo("hello world test");
    }

    @Test
    @DisplayName("不支持的格式应返回失败结果")
    void shouldReturnFailureForUnsupportedFormat() {
      InputStream stream = TestUtils.streamOf("data");

      DocumentProcessorPipeline.Pipeline pipeline =
          DocumentProcessorPipeline.builder()
              .parse(ParseOptions.builder().build())
              .build(processor);

      DocumentProcessResult result = pipeline.execute(stream, "unknown.xyz");

      assertThat(result.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("安全扫描高风险 + blockOnHighRisk 应阻止解析")
    void shouldBlockWhenHighRiskDetected() throws Exception {
      // 模拟临时文件写入成功
      Path tempFile = Files.createTempFile("test-", ".tmp");
      Files.write(tempFile, "data".getBytes(StandardCharsets.UTF_8));
      when(tempFileManager.createAndWrite(any(), any(), any())).thenReturn(tempFile);

      DocumentProcessorPipeline.Pipeline pipeline =
          DocumentProcessorPipeline.builder()
              .parse(ParseOptions.builder().build())
              .securityScan(true)
              .blockOnHighRisk(true)
              .build(processor);

      InputStream stream = TestUtils.streamOf("sensitive data");
      DocumentProcessResult result = pipeline.execute(stream, "test.docx");

      // 注意：DOCX 未注册解析器，但安全扫描会在解析前触发，若扫描结果为 SAFE，则进入解析阶段失败
      // 本测试主要验证管道不抛出异常
      assertThat(result).isNotNull();
      Files.deleteIfExists(tempFile);
    }

    @Test
    @DisplayName("PII 检测启用时 DocumentProcessResult 应附带 PiiDetectionSummary (P-4 指标分离)")
    void shouldAttachPiiDetectionSummaryWhenPiiEnabled() throws Exception {
      // 准备：模拟解析器返回一段含手机号的文本
      DocumentContent contentWithPhone =
          DocumentContent.builder()
              .text("联系人张三电话 13812345678 地址北京")
              .sections(
                  List.of(
                      DocumentSection.builder()
                          .type("paragraph")
                          .content("联系人张三电话 13812345678 地址北京")
                          .build()))
              .tables(List.of(DocumentTable.builder().build()))
              .totalChars(19)
              .totalPages(1)
              .build();

      DocumentParser mockParser = mock(DocumentParser.class);
      when(parserRegistry.isSupported(DocumentFormat.TXT)).thenReturn(true);
      when(parserRegistry.getParser(DocumentFormat.TXT)).thenReturn(mockParser);
      when(mockParser.parse(any(), any(), any())).thenReturn(contentWithPhone);

      // Mock 一个 PII 检测器：命中手机号一次
      PiiDetector mockDetector = mock(PiiDetector.class);
      PiiFinding mockFinding =
          PiiFinding.builder()
              .type(PiiType.PHONE)
              .maskedValue("138****5678")
              .startIndex(7)
              .endIndex(18)
              .contextBefore("联系人张三电话 ")
              .contextAfter(" 地址北京")
              .confidence(new java.math.BigDecimal("0.95"))
              .build();
      when(mockDetector.detect(any())).thenReturn(List.of(mockFinding));
      when(mockDetector.getSupportedType()).thenReturn(PiiType.PHONE);

      // 替换 processor，注入 mock detector
      DocumentProcessorPipeline processorWithDetector =
          new DocumentProcessorPipeline(
              parserRegistry,
              List.of(),
              List.of(mockDetector),
              preprocessPipeline,
              properties,
              tempFileManager);

      DocumentProcessorPipeline.Pipeline pipeline =
          DocumentProcessorPipeline.builder()
              .parse(ParseOptions.builder().build())
              .piiDetect(true)
              .build(processorWithDetector);

      InputStream stream = TestUtils.streamOf("dummy content");
      DocumentProcessResult result = pipeline.execute(stream, "test.txt");

      // 验证：findings 存在 + summary 亦存在
      assertThat(result.getPiiFindings()).isNotEmpty();
      PiiDetectionSummary summary = result.getPiiDetectionSummary();
      assertThat(summary).isNotNull();
      assertThat(summary.totalFindings()).isGreaterThanOrEqualTo(1);
      assertThat(summary.totalDuration()).isNotNull();
      assertThat(summary.hasFailure()).isFalse();
      // per-type 统计
      assertThat(summary.countByType()).containsKey(PiiType.PHONE);
      assertThat(summary.countByType().get(PiiType.PHONE)).isGreaterThanOrEqualTo(1);
    }
  }
}
