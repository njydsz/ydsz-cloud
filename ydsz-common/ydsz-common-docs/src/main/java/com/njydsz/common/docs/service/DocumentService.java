package com.njydsz.common.docs.service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.njydsz.common.docs.config.DocsProperties;
import com.njydsz.common.docs.convert.DocumentConverter;
import com.njydsz.common.docs.domain.DocumentContent;
import com.njydsz.common.docs.domain.DocumentParseResult;
import com.njydsz.common.docs.domain.ParseOptions;
import com.njydsz.common.docs.domain.PiiFinding;
import com.njydsz.common.docs.domain.SecurityScanResult;
import com.njydsz.common.docs.enums.DocumentFormat;
import com.njydsz.common.docs.enums.SecurityLevel;
import com.njydsz.common.docs.exception.DocumentException;
import com.njydsz.common.docs.exception.DocumentExceptionCode;
import com.njydsz.common.docs.metrics.DocsMetrics;
import com.njydsz.common.docs.ocr.OcrEngine;
import com.njydsz.common.docs.ocr.OcrProvider;
import com.njydsz.common.docs.parser.registry.DocumentParserRegistry;
import com.njydsz.common.docs.preprocess.pipeline.PreprocessPipeline;
import com.njydsz.common.docs.security.pii.PiiDetector;
import com.njydsz.common.docs.security.scanner.DocumentSecurityScanner;
import com.njydsz.common.util.io.TempFileManager;

/**
 * 文档处理统一服务门面
 *
 * <p>整合文档解析、预处理、安全扫描和 PII 检测能力，对外提供一站式 API。
 *
 * <p><b>核心方法：</b>
 *
 * <ul>
 *   <li>{@link #parse} - 解析文档内容
 *   <li>{@link #preprocess} - 预处理文档内容
 *   <li>{@link #scanSecurity} - 安全扫描
 *   <li>{@link #detectPii} - PII 检测
 *   <li>{@link #parseAndPreprocess} - 解析 + 预处理一体化
 *   <li>{@link #parseWithSecurityCheck} - 解析 + 安全扫描一体化
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
public class DocumentService {

  private final DocumentParserRegistry parserRegistry;
  private final PreprocessPipeline preprocessPipeline;
  private final List<DocumentSecurityScanner> securityScanners;
  private final List<PiiDetector> piiDetectors;
  private final DocsProperties properties;
  private final ObjectProvider<DocumentConverter> converterProvider;
  private final ObjectProvider<OcrProvider> ocrProvider;
  private final ObjectProvider<DocsMetrics> metricsProvider;
  private final TempFileManager tempFileManager;

  public DocumentService(
      DocumentParserRegistry parserRegistry,
      PreprocessPipeline preprocessPipeline,
      List<DocumentSecurityScanner> securityScanners,
      List<PiiDetector> piiDetectors,
      DocsProperties properties,
      ObjectProvider<DocumentConverter> converterProvider,
      ObjectProvider<OcrProvider> ocrProvider,
      ObjectProvider<DocsMetrics> metricsProvider,
      TempFileManager tempFileManager) {
    this.parserRegistry = parserRegistry;
    this.preprocessPipeline = preprocessPipeline;
    this.securityScanners = securityScanners;
    this.piiDetectors = piiDetectors;
    this.properties = properties;
    this.converterProvider = converterProvider;
    this.ocrProvider = ocrProvider;
    this.metricsProvider = metricsProvider;
    this.tempFileManager = tempFileManager;
  }

  /**
   * 解析文档
   *
   * @param inputStream 文档输入流
   * @param fileName 文件名
   * @param options 解析选项
   * @return 解析结果
   */
  public DocumentParseResult parse(InputStream inputStream, String fileName, ParseOptions options) {
    Instant start = Instant.now();
    DocumentFormat format = DocumentFormat.UNKNOWN;
    try {
      format = DocumentFormat.fromFileName(fileName);
      if (format == DocumentFormat.UNKNOWN) {
        throw new DocumentException(
            DocumentExceptionCode.UNSUPPORTED_FORMAT, "无法识别文档格式: " + fileName);
      }

      if (format == DocumentFormat.DOC || format == DocumentFormat.PPT) {
        throw new DocumentException(
            DocumentExceptionCode.UNSUPPORTED_FORMAT,
            "旧格式 ." + format.getExtension() + " 建议转换为新格式后再上传");
      }

      if (!parserRegistry.isSupported(format)) {
        throw new DocumentException(
            DocumentExceptionCode.UNSUPPORTED_FORMAT, "暂不支持解析此格式: " + format);
      }

      var parser = parserRegistry.getParser(format);
      DocumentContent content = parser.parse(inputStream, fileName, options);

      Duration elapsed = Duration.between(start, Instant.now());
      recordParseMetric(format, true, elapsed.toMillis());
      return DocumentParseResult.builder()
          .content(content)
          .elapsed(elapsed)
          .success(true)
          .fileName(fileName)
          .build();

    } catch (DocumentException e) {
      recordParseMetric(format, false, Duration.between(start, Instant.now()).toMillis());
      return DocumentParseResult.builder()
          .elapsed(Duration.between(start, Instant.now()))
          .success(false)
          .errorMessage(e.getMessage())
          .fileName(fileName)
          .build();
    } catch (Exception e) {
      log.error("[DocumentService] 解析异常: {}", fileName, e);
      recordParseMetric(format, false, Duration.between(start, Instant.now()).toMillis());
      return DocumentParseResult.builder()
          .elapsed(Duration.between(start, Instant.now()))
          .success(false)
          .errorMessage(e.getMessage())
          .fileName(fileName)
          .build();
    }
  }

  /**
   * 预处理文档内容
   *
   * @param content 原始文档内容
   * @return 预处理后的文档内容
   */
  public DocumentContent preprocess(DocumentContent content) {
    if (!properties.isPreprocessEnabled()) {
      return content;
    }
    return preprocessPipeline.execute(content);
  }

  /**
   * 解析 + 预处理一体化
   *
   * @param inputStream 文档输入流
   * @param fileName 文件名
   * @param options 解析选项
   * @return 解析并预处理后的结果
   */
  public DocumentParseResult parseAndPreprocess(
      InputStream inputStream, String fileName, ParseOptions options) {
    DocumentParseResult parseResult = parse(inputStream, fileName, options);
    if (parseResult.isSuccess() && parseResult.getContent() != null) {
      DocumentContent processed = preprocess(parseResult.getContent());
      parseResult.setContent(processed);
    }
    return parseResult;
  }

  /**
   * 安全扫描
   *
   * @param inputStream 文档输入流
   * @param fileName 文件名
   * @return 安全扫描结果
   */
  public SecurityScanResult scanSecurity(InputStream inputStream, String fileName) {
    if (!properties.isSecurityScanEnabled()) {
      return SecurityScanResult.builder()
          .securityLevel(SecurityLevel.SAFE)
          .findings(List.of())
          .success(true)
          .build();
    }
    DocumentFormat format = DocumentFormat.fromFileName(fileName);
    SecurityScanResult result = doScanSecurity(inputStream, fileName, format);
    recordSecurityScanMetric(result.getSecurityLevel());
    return result;
  }

  /**
   * PII 检测
   *
   * @param content 文档内容
   * @return PII 发现列表
   */
  public List<PiiFinding> detectPii(DocumentContent content) {
    if (!properties.isPiiDetectionEnabled()) {
      return List.of();
    }
    List<PiiFinding> findings = doDetectPii(content);
    recordPiiMetric(findings);
    return findings;
  }

  /**
   * 将文档转换为目标格式。
   *
   * <p>源格式由文件名后缀推断，实际转换委托给容器中注册的 {@link DocumentConverter} （通常对接 LibreOffice / OnlyOffice
   * 等外部转换服务）。本模块只定义接口， 未引入具体实现时直接抛异常而非静默返回空内容，避免调用方拿到损坏文件。
   *
   * <p>方法会把结果完整读入内存，大文件转换需评估堆内存占用。
   *
   * @param inputStream 源文档流，由调用方负责关闭
   * @param fileName 原始文件名，用于推断源格式，不可为 {@code null}
   * @param targetFormat 目标格式，不可为 {@code null}
   * @return 转换后的文档字节数组
   * @throws DocumentException 转换器未注册或转换失败时抛出，错误码 {@code CONVERT_FAILED}
   */
  public byte[] convert(InputStream inputStream, String fileName, DocumentFormat targetFormat) {
    DocumentConverter converter = converterProvider.getIfAvailable();
    if (converter == null) {
      throw new DocumentException(DocumentExceptionCode.CONVERT_FAILED, "文档转换器未注册");
    }
    DocumentFormat sourceFormat = DocumentFormat.fromFileName(fileName);
    return converter.convert(inputStream, fileName, sourceFormat, targetFormat);
  }

  /**
   * 对扫描件 / 图片型文档执行 OCR 文字识别。
   *
   * <p>识别过程依赖外部 OCR 引擎（本地 Tesseract 或云厂商 API）， 属于高耗时且可能产生调用费用的操作，建议由异步任务触发而非同步接口直调。
   * 识别准确率受图片质量影响，返回内容需业务侧二次校验，不应直接用于强校验场景。
   *
   * @param inputStream 源文档流，由调用方负责关闭
   * @param fileName 原始文件名
   * @param engine 指定使用的 OCR 引擎
   * @return 识别出的文档内容
   * @throws DocumentException OCR 提供者未注册或识别失败时抛出，错误码 {@code PARSE_FAILED}
   */
  public DocumentContent ocrScan(InputStream inputStream, String fileName, OcrEngine engine) {
    OcrProvider provider = ocrProvider.getIfAvailable();
    if (provider == null) {
      throw new DocumentException(DocumentExceptionCode.PARSE_FAILED, "OCR 提供者未注册");
    }
    return provider.ocrScan(inputStream, fileName, engine);
  }

  /**
   * 解析 + 安全扫描一体化
   *
   * <p>高风险时可阻止返回。
   *
   * @param inputStream 文档输入流
   * @param fileName 文件名
   * @param options 解析选项
   * @return 解析结果（高风险时 success=false）
   */
  public DocumentParseResult parseWithSecurityCheck(
      InputStream inputStream, String fileName, ParseOptions options) {
    Path tempFile = null;
    try {
      tempFile = tempFileManager.createAndWrite("ydsz-docs-sec-", ".tmp", inputStream);

      SecurityScanResult scanResult = scanSecurity(Files.newInputStream(tempFile), fileName);
      if (properties.isBlockOnHighRisk()
          && scanResult.getSecurityLevel() != null
          && scanResult.getSecurityLevel().ordinal() >= SecurityLevel.HIGH.ordinal()) {
        log.warn(
            "[DocumentService] 文档安全扫描未通过，已阻止解析: fileName={}, level={}",
            fileName,
            scanResult.getSecurityLevel());
        return DocumentParseResult.builder()
            .success(false)
            .errorMessage("文档存在高危安全风险(" + scanResult.getSecurityLevel() + ")，已被安全策略阻止")
            .fileName(fileName)
            .elapsed(Duration.ZERO)
            .build();
      }
      return parse(Files.newInputStream(tempFile), fileName, options);
    } catch (Exception e) {
      return DocumentParseResult.builder()
          .success(false)
          .errorMessage(e.getMessage())
          .fileName(fileName)
          .elapsed(Duration.ZERO)
          .build();
    } finally {
      tempFileManager.deleteTracked(tempFile);
    }
  }

  // ==================== Composite Logic (inlined) ====================

  /** 执行安全扫描：遍历所有已注册的扫描器并聚合结果。 */
  private SecurityScanResult doScanSecurity(
      InputStream inputStream, String fileName, DocumentFormat format) {
    if (inputStream == null) {
      return SecurityScanResult.builder()
          .securityLevel(SecurityLevel.SAFE)
          .findings(List.of())
          .success(false)
          .errorMessage("输入流为空")
          .build();
    }

    Path tempFile = null;
    try {
      tempFile = tempFileManager.createAndWrite("ydsz-docs-scan-", ".tmp", inputStream);

      List<SecurityScanResult.SecurityFinding> allFindings = new java.util.ArrayList<>();
      boolean allSuccess = true;
      String lastError = null;

      for (DocumentSecurityScanner scanner : securityScanners) {
        try (InputStream fis = Files.newInputStream(tempFile)) {
          SecurityScanResult result = scanner.scan(fis, fileName, format);
          if (result.isSuccess()) {
            if (result.getFindings() != null) {
              allFindings.addAll(result.getFindings());
            }
          } else {
            allSuccess = false;
            lastError = result.getErrorMessage();
            log.warn("[DocumentService] 安全扫描器 {} 执行失败: {}", scanner.getName(), lastError);
          }
        } catch (Exception e) {
          allSuccess = false;
          log.error("[DocumentService] 安全扫描器 {} 异常", scanner.getName(), e);
        }
      }

      SecurityLevel level =
          allFindings.isEmpty()
              ? SecurityLevel.SAFE
              : allFindings.stream()
                  .map(SecurityScanResult.SecurityFinding::getLevel)
                  .max(Enum::compareTo)
                  .orElse(SecurityLevel.SAFE);

      return SecurityScanResult.builder()
          .securityLevel(level)
          .findings(allFindings)
          .success(allSuccess)
          .errorMessage(lastError)
          .build();

    } catch (Exception e) {
      log.error("[DocumentService] 安全扫描临时文件写入失败", e);
      return SecurityScanResult.builder()
          .securityLevel(SecurityLevel.SAFE)
          .findings(List.of())
          .success(false)
          .errorMessage("IO 错误: " + e.getMessage())
          .build();
    } finally {
      tempFileManager.deleteTracked(tempFile);
    }
  }

  /** 执行 PII 检测：遍历所有已注册的检测器并聚合结果。 */
  private List<PiiFinding> doDetectPii(DocumentContent content) {
    if (content == null || content.getText() == null) {
      return List.of();
    }

    List<PiiFinding> allFindings = new java.util.ArrayList<>();
    for (PiiDetector detector : piiDetectors) {
      try {
        List<PiiFinding> findings = detector.detect(content);
        if (findings != null) {
          allFindings.addAll(findings);
        }
      } catch (Exception e) {
        log.error("[DocumentService] PII 检测器 {} 执行失败", detector.getSupportedType(), e);
      }
    }
    return allFindings;
  }

  // ==================== Metrics Helpers ====================

  private void recordParseMetric(DocumentFormat format, boolean success, long durationMs) {
    DocsMetrics metrics = metricsProvider.getIfAvailable();
    if (metrics != null) {
      metrics.recordParse(format, success, durationMs);
    }
  }

  private void recordPiiMetric(List<PiiFinding> findings) {
    DocsMetrics metrics = metricsProvider.getIfAvailable();
    if (metrics != null && findings != null && !findings.isEmpty()) {
      findings.stream()
          .collect(Collectors.groupingBy(PiiFinding::getType))
          .forEach((type, list) -> metrics.recordPiiDetected(type, list.size()));
    }
  }

  private void recordSecurityScanMetric(SecurityLevel level) {
    DocsMetrics metrics = metricsProvider.getIfAvailable();
    if (metrics != null && level != null) {
      metrics.recordSecurityScan(level);
    }
  }
}
