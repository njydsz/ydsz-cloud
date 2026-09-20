package com.njydsz.common.docs.pipeline;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.common.docs.config.DocsProperties;
import com.njydsz.common.docs.domain.DocumentContent;
import com.njydsz.common.docs.domain.DocumentParseResult;
import com.njydsz.common.docs.domain.ParseOptions;
import com.njydsz.common.docs.domain.PiiFinding;
import com.njydsz.common.docs.domain.SecurityScanResult;
import com.njydsz.common.docs.enums.PiiType;
import com.njydsz.common.docs.enums.SecurityLevel;
import com.njydsz.common.docs.exception.DocumentException;
import com.njydsz.common.docs.exception.DocumentExceptionCode;
import com.njydsz.common.docs.parser.DocumentParser;
import com.njydsz.common.docs.parser.registry.DocumentParserRegistry;
import com.njydsz.common.docs.preprocess.pipeline.PreprocessPipeline;
import com.njydsz.common.docs.security.pii.PiiDetectionSummary;
import com.njydsz.common.docs.security.pii.PiiDetector;
import com.njydsz.common.docs.security.scanner.DocumentSecurityScanner;
import com.njydsz.common.util.io.TempFileManager;

/**
 * 文档处理管道 —— 链式 Builder 模式统一编排解析、安全扫描、PII 检测与预处理。
 *
 * <p>替代 {@code DocumentService} 中 N 个"一体化"方法（parseAndPreprocess、parseWithSecurityCheck 等），
 * 避免每新增一种组合就必须新增一个方法的爆炸式增长。
 *
 * <h3>典型用法</h3>
 *
 * <pre>{@code
 * DocumentProcessorPipeline pipeline = DocumentProcessorPipeline.builder()
 *     .parse(ParseOptions.defaults())
 *     .securityScan(true)
 *     .piiDetect(true)
 *     .preprocess(true)
 *     .build();
 *
 * DocumentProcessResult result = pipeline.execute(inputStream, fileName);
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
public class DocumentProcessorPipeline {

  private final DocumentParserRegistry parserRegistry;
  private final List<DocumentSecurityScanner> securityScanners;
  private final List<PiiDetector> piiDetectors;
  private final PreprocessPipeline preprocessPipeline;
  private final DocsProperties properties;
  private final TempFileManager tempFileManager;

  public DocumentProcessorPipeline(
      DocumentParserRegistry parserRegistry,
      List<DocumentSecurityScanner> securityScanners,
      List<PiiDetector> piiDetectors,
      PreprocessPipeline preprocessPipeline,
      DocsProperties properties,
      TempFileManager tempFileManager) {
    this.parserRegistry = parserRegistry;
    this.securityScanners = securityScanners;
    this.piiDetectors = piiDetectors;
    this.preprocessPipeline = preprocessPipeline;
    this.properties = properties;
    this.tempFileManager = tempFileManager;
  }

  /**
   * 创建 Builder 实例。
   *
   * @return 新的 Builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * 执行容器装配 —— 由 Builder.build() 调用以拿到不可变的执行快照。
   *
   * @return 可执行的管道快照
   */
  private Pipeline build(Builder builder) {
    return new Pipeline(builder);
  }

  /** 管道 Builder。 */
  public static class Builder {

    private ParseOptions parseOptions;
    private boolean securityScanEnabled;
    private boolean piiDetectEnabled;
    private boolean preprocessEnabled;
    private boolean blockOnHighRisk;

    Builder() {
      // 仅在包内实例化
    }

    /**
     * 设置解析选项。
     *
     * @param options 解析选项，不可为 {@code null}
     * @return this
     */
    public Builder parse(ParseOptions options) {
      this.parseOptions = Objects.requireNonNull(options, "parseOptions 不可为 null");
      return this;
    }

    /**
     * 启用安全扫描。
     *
     * @param enabled 是否启用
     * @return this
     */
    public Builder securityScan(boolean enabled) {
      this.securityScanEnabled = enabled;
      return this;
    }

    /**
     * 启用 PII 检测。
     *
     * @param enabled 是否启用
     * @return this
     */
    public Builder piiDetect(boolean enabled) {
      this.piiDetectEnabled = enabled;
      return this;
    }

    /**
     * 启用预处理。
     *
     * @param enabled 是否启用
     * @return this
     */
    public Builder preprocess(boolean enabled) {
      this.preprocessEnabled = enabled;
      return this;
    }

    /**
     * 发现高风险时是否阻止解析。
     *
     * @param block 是否阻止
     * @return this
     */
    public Builder blockOnHighRisk(boolean block) {
      this.blockOnHighRisk = block;
      return this;
    }

    /**
     * 构建不可变管道实例。
     *
     * <p>注意：此方法需要容器中的 {@link DocumentProcessorPipeline} Bean，
     * 因此必须在 Spring 容器内调用；或者通过静态工厂方法传入组件依赖。
     *
     * @param processor 已装配的 DocumentProcessorPipeline Bean
     * @return 可执行的管道快照
     */
    public Pipeline build(DocumentProcessorPipeline processor) {
      Objects.requireNonNull(processor, "processor 不可为 null");
      return processor.build(this);
    }
  }

  /** 不可变的管道快照 —— 持有 Builder 阶段的配置，可独立执行。 */
  public class Pipeline {

    private final ParseOptions parseOptions;
    private final boolean securityScanEnabled;
    private final boolean piiDetectEnabled;
    private final boolean preprocessEnabled;
    private final boolean blockOnHighRisk;

    private Pipeline(Builder builder) {
      this.parseOptions = builder.parseOptions != null
          ? builder.parseOptions
          : com.njydsz.common.docs.domain.ParseOptions.builder().build();
      this.securityScanEnabled = builder.securityScanEnabled;
      this.piiDetectEnabled = builder.piiDetectEnabled;
      this.preprocessEnabled = builder.preprocessEnabled;
      this.blockOnHighRisk = builder.blockOnHighRisk;
    }

    /**
     * 执行管道：按"安全扫描 → 解析 → PII 检测 → 预处理"的顺序处理输入流。
     *
     * @param inputStream 文档输入流，由调用方负责关闭
     * @param fileName 原始文件名
     * @return 管道处理结果
     */
    public DocumentProcessResult execute(InputStream inputStream, String fileName) {
      DocumentProcessResult result = new DocumentProcessResult();

      // 1. 安全扫描（可选）
      if (securityScanEnabled && properties.isSecurityScanEnabled()) {
        try {
          SecurityScanResult scanResult = doScanSecurity(inputStream, fileName);
          result.setSecurityScanResult(scanResult);
          if (blockOnHighRisk
              && scanResult.getSecurityLevel() != null
              && scanResult.getSecurityLevel().ordinal() >= SecurityLevel.HIGH.ordinal()) {
            result.setParseResult(
                DocumentParseResult.builder()
                    .isSuccess(false)
                    .errorMessage("文档存在高危安全风险(" + scanResult.getSecurityLevel() + ")，已被安全策略阻止")
                    .fileName(fileName)
                    .build());
            return result;
          }
        } catch (Exception e) {
          log.warn("[Pipeline] 安全扫描阶段异常: {}", e.getMessage());
        }
      }

      // 2. 解析
      try {
        var format = com.njydsz.common.docs.enums.DocumentFormat.fromFileName(fileName);
        if (!parserRegistry.isSupported(format)) {
          result.setParseResult(
              DocumentParseResult.builder()
                  .isSuccess(false)
                  .errorMessage("不支持的文档格式: " + format)
                  .fileName(fileName)
                  .build());
          return result;
        }
        DocumentParser parser = parserRegistry.getParser(format);
        DocumentContent content = parser.parse(inputStream, fileName, parseOptions);
        result.setParseResult(
            DocumentParseResult.builder()
                .isSuccess(true)
                .content(content)
                .fileName(fileName)
                .build());

        // 3. PII 检测（可选）
        if (piiDetectEnabled && properties.isPiiDetectionEnabled() && content != null) {
          PiiDetectionResult piiResult = doDetectPiiWithMetrics(content);
          result.setPiiFindings(piiResult.findings());
          result.setPiiDetectionSummary(piiResult.summary());
        }

        // 4. 预处理（可选）
        if (preprocessEnabled && properties.isPreprocessEnabled() && content != null) {
          DocumentContent processed = preprocessPipeline.execute(content);
          result.getParseResult().setContent(processed);
        }
      } catch (Exception e) {
        result.setParseResult(
            DocumentParseResult.builder()
                .isSuccess(false)
                .errorMessage(e.getMessage())
                .fileName(fileName)
                .build());
      }

      return result;
    }

    private SecurityScanResult doScanSecurity(InputStream inputStream, String fileName) {
      var format = com.njydsz.common.docs.enums.DocumentFormat.fromFileName(fileName);
      Path tempFile;
      try {
        tempFile = tempFileManager.createAndWrite("ydsz-docs-pipeline-", ".tmp", inputStream);
      } catch (IOException e) {
        throw new DocumentException(DocumentExceptionCode.SECURITY_SCAN_FAILED, e);
      }
      try {
        List<SecurityScanResult.SecurityFinding> allFindings = new ArrayList<>(16);
        try (InputStream fis = Files.newInputStream(tempFile)) {
          for (var scanner : securityScanners) {
            SecurityScanResult scanResult = scanner.scan(fis, fileName, format);
            if (scanResult.isSuccess() && scanResult.getFindings() != null) {
              allFindings.addAll(scanResult.getFindings());
            }
          }
        } catch (IOException e) {
          throw new DocumentException(DocumentExceptionCode.SECURITY_SCAN_FAILED, e);
        }
        SecurityLevel level = allFindings.isEmpty()
            ? SecurityLevel.SAFE
            : allFindings.stream()
                .map(SecurityScanResult.SecurityFinding::getLevel)
                .max(Enum::compareTo)
                .orElse(SecurityLevel.SAFE);
        return SecurityScanResult.builder()
            .securityLevel(level)
            .findings(allFindings)
            .isSuccess(true)
            .build();
      } finally {
        tempFileManager.deleteTracked(tempFile);
      }
    }

    /**
     * 执行 PII 检测并同步收集运行时指标（P-4 指标分离）。
     *
     * <p>保持原有 doDetectPii 签名不变以兼容单元测试； 本方法是其增强版本，额外采集各类型命中数与总耗时， 分别通过 {@link PiiDetectionResult#findings()} 与 {@link PiiDetectionResult#summary()} 暴露。
     *
     * @param content 已解析的文档内容
     * @return 纯 findings 列表 + 检测指标
     */
    private PiiDetectionResult doDetectPiiWithMetrics(DocumentContent content) {
      if (content == null || content.getText() == null) {
        return new PiiDetectionResult(List.of(), null);
      }
      Instant start = Instant.now();
      List<PiiFinding> allFindings = new ArrayList<>(16);
      EnumMap<PiiType, Integer> countByType = new EnumMap<>(PiiType.class);
      int failures = 0;

      for (PiiDetector detector : piiDetectors) {
        try {
          List<PiiFinding> findings = detector.detect(content);
          if (findings != null) {
            allFindings.addAll(findings);
            PiiType type = detector.getSupportedType();
            countByType.merge(type, findings.size(), Integer::sum);
          }
        } catch (Exception e) {
          failures++;
          log.error("[Pipeline] PII 检测器 {} 执行失败", detector.getSupportedType(), e);
        }
      }

      Duration elapsed = Duration.between(start, Instant.now());
      PiiDetectionSummary summary = new PiiDetectionSummary(
          elapsed,
          allFindings.size(),
          failures,
          countByType);
      return new PiiDetectionResult(allFindings, summary);
    }

    /** PII 检测的纯结果 + 指标（管道内部传输用） */
    private record PiiDetectionResult(List<PiiFinding> findings, PiiDetectionSummary summary) {}
  }
}
