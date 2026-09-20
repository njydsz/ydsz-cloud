package com.njydsz.common.docs.security.scanner;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.poi.poifs.filesystem.DirectoryEntry;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.njydsz.common.docs.domain.SecurityScanResult;
import com.njydsz.common.docs.enums.DocumentFormat;
import com.njydsz.common.docs.enums.SecurityLevel;

/**
 * {@link Ole2EmbeddedObjectScanner} 单元测试。
 *
 * <p>内联使用 {@link POIFSFileSystem} 构建测试用 OLE2 容器， 通过 InputStream 输入，覆盖核心检测路径。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
class Ole2EmbeddedObjectScannerTest {

  private Ole2EmbeddedObjectScanner scanner;

  @BeforeEach
  void setUp() {
    scanner = new Ole2EmbeddedObjectScanner();
  }

  @Nested
  @DisplayName("getName()")
  class WhenGetName {

    @Test
    @DisplayName("应返回常量名称 ole2-embedded-scanner")
    void shouldReturnConstantName() {
      assertThat(scanner.getName()).isEqualTo("ole2-embedded-scanner");
    }
  }

  @Nested
  @DisplayName("非旧版 OLE2 格式输入")
  class WhenGivenNonOle2Format {

    @Test
    @DisplayName("PDF 格式应直接放行，标记 SAFE")
    void shouldReturnSafeForPdf() {
      SecurityScanResult result = scanner.scan(emptyStream(), "sample.pdf", DocumentFormat.PDF);

      assertThat(result.getSecurityLevel()).isEqualTo(SecurityLevel.SAFE);
      assertThat(result.getFindings()).isEmpty();
      assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("DOCX 格式(OOXML)应直接放行，由 MacroDetector 管辖")
    void shouldReturnSafeForDocx() {
      SecurityScanResult result = scanner.scan(emptyStream(), "sample.docx", DocumentFormat.DOCX);

      assertThat(result.getSecurityLevel()).isEqualTo(SecurityLevel.SAFE);
      assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("null 格式应安全放行")
    void shouldHandleNullFormat() {
      SecurityScanResult result = scanner.scan(emptyStream(), "sample.unknown", null);

      assertThat(result.getSecurityLevel()).isEqualTo(SecurityLevel.SAFE);
      assertThat(result.isSuccess()).isTrue();
    }
  }

  @Nested
  @DisplayName("旧版 OLE2 格式 (.doc / .xls / .ppt) 路径")
  class WhenGivenLegacyCompoundFormat {

    @Test
    @DisplayName("空 OLE2 容器应返回 SAFE，无发现")
    void shouldReturnSafeForEmptyOle2Container() throws IOException {
      byte[] oleBytes = buildOle2Container(root -> {});
      SecurityScanResult result =
          scanner.scan(new ByteArrayInputStream(oleBytes), "report.doc", DocumentFormat.DOC);

      assertThat(result.getSecurityLevel()).isEqualTo(SecurityLevel.SAFE);
      assertThat(result.isSuccess()).isTrue();
      assertThat(result.getFindings()).isEmpty();
    }

    @Test
    @DisplayName("包含 \\u0001Ole10Native 流的文档应标记 MEDIUM embedded_object")
    void shouldFlagOle10NativeStream() throws IOException {
      byte[] oleBytes =
          buildOle2Container(
              root -> {
                byte[] stub = new byte[100];
                root.createDocument("\u0001Ole10Native", new ByteArrayInputStream(stub));
              });
      SecurityScanResult result =
          scanner.scan(new ByteArrayInputStream(oleBytes), "report.doc", DocumentFormat.DOC);

      assertThat(result.getSecurityLevel()).isEqualTo(SecurityLevel.MEDIUM);
      assertThat(result.getFindings())
          .anyMatch(
              f ->
                  "embedded_object".equals(f.getType())
                      && f.getLevel() == SecurityLevel.MEDIUM);
    }

    @Test
    @DisplayName("包含 Macros 目录的文档应标记 HIGH macro")
    void shouldFlagVbaMacroDirectory() throws IOException {
      byte[] oleBytes =
          buildOle2Container(
              root -> {
                root.createDirectory("Macros");
              });
      SecurityScanResult result =
          scanner.scan(new ByteArrayInputStream(oleBytes), "data.xls", DocumentFormat.XLS);

      assertThat(result.getSecurityLevel()).isEqualTo(SecurityLevel.HIGH);
      assertThat(result.getFindings())
          .anyMatch(f -> "macro".equals(f.getType()) && f.getLevel() == SecurityLevel.HIGH);
    }

    @Test
    @DisplayName("包含 100KB 扇区的文档应标记 HIGH 大文件发现")
    void shouldFlagLargeEmbeddedData() throws IOException {
      byte[] oleBytes =
          buildOle2Container(
              root -> {
                byte[] data = new byte[100 * 1024];
                root.createDocument("EmbeddedData", new ByteArrayInputStream(data));
              });
      SecurityScanResult result =
          scanner.scan(new ByteArrayInputStream(oleBytes), "data.ppt", DocumentFormat.PPT);

      assertThat(result.getSecurityLevel()).isEqualTo(SecurityLevel.HIGH);
      assertThat(result.getFindings())
          .anyMatch(
              f ->
                  "embedded_object".equals(f.getType())
                      && f.getLevel() == SecurityLevel.HIGH);
    }

    @Test
    @DisplayName("包含 MZ 签名的可执行文件嵌入应标记 CRITICAL")
    void shouldFlagMzSignature() throws IOException {
      byte[] exeStub = new byte[4096];
      exeStub[0] = 'M';
      exeStub[1] = 0;
      exeStub[2] = 'Z';
      exeStub[3] = 0;

      byte[] oleBytes =
          buildOle2Container(
              root -> {
                root.createDocument(
                    "Payload", new ByteArrayInputStream(exeStub));
              });
      SecurityScanResult result =
          scanner.scan(new ByteArrayInputStream(oleBytes), "weapon.doc", DocumentFormat.DOC);

      assertThat(result.getSecurityLevel()).isEqualTo(SecurityLevel.CRITICAL);
      assertThat(result.getFindings())
          .anyMatch(
              f ->
                  "embedded_object".equals(f.getType())
                      && f.getLevel() == SecurityLevel.CRITICAL
                      && f.getDescription().contains("MZ"));
    }

    @Test
    @DisplayName("不含可执行文件但有 OLE 嵌入的多个风险项应汇总并返回最严格级别")
    void shouldReturnHighestLevelAmongFindings() throws IOException {
      byte[] oleBytes =
          buildOle2Container(
              root -> {
                byte[] stub = new byte[200];
                root.createDocument("\u0001CompObj", new ByteArrayInputStream(stub));
                root.createDirectory("Macros");
              });
      SecurityScanResult result =
          scanner.scan(new ByteArrayInputStream(oleBytes), "combo.doc", DocumentFormat.DOC);

      assertThat(result.getFindings()).hasSize(2);
      assertThat(result.getSecurityLevel()).isEqualTo(SecurityLevel.HIGH);
    }
  }

  @Nested
  @DisplayName("异常流与故障")
  class WhenInputStreamErrors {

    @Test
    @DisplayName("非 OLE2 规范输入应标记 isSuccess=false，不抛异常")
    void shouldReportFailureForMalformedOle2() {
      byte[] bogus = "I am not an OLE2 file — just plaintext".getBytes();
      SecurityScanResult result =
          scanner.scan(
              new ByteArrayInputStream(bogus), "corrupt.doc", DocumentFormat.DOC);

      assertThat(result.isSuccess()).isFalse();
      assertThat(result.getSecurityLevel()).isEqualTo(SecurityLevel.SAFE);
    }
  }

  private static ByteArrayInputStream emptyStream() {
    return new ByteArrayInputStream(new byte[0]);
  }

  private static byte[] buildOle2Container(Ole2ContainerConfigurator configurator)
      throws IOException {
    try (POIFSFileSystem fs = new POIFSFileSystem()) {
      DirectoryEntry root = fs.getRoot();
      configurator.configure(root);
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      fs.writeFilesystem(bos);
      return bos.toByteArray();
    }
  }

  /** 测试用 OLE2 容器构造回调。 */
  @FunctionalInterface
  private interface Ole2ContainerConfigurator {
    void configure(DirectoryEntry root) throws IOException;
  }
}
