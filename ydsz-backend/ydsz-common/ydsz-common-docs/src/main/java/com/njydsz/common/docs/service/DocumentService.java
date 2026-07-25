package com.njydsz.common.docs.service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

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
import com.njydsz.common.docs.enums.PiiType;
import com.njydsz.common.docs.enums.SecurityLevel;
import com.njydsz.common.docs.exception.DocumentException;
import com.njydsz.common.docs.exception.DocumentExceptionCode;
import com.njydsz.common.docs.metrics.DocsMetrics;
import com.njydsz.common.docs.ocr.OcrEngine;
import com.njydsz.common.docs.ocr.OcrProvider;
import com.njydsz.common.docs.parser.registry.DocumentParserRegistry;
import com.njydsz.common.docs.preprocess.pipeline.PreprocessPipeline;
import com.njydsz.common.docs.security.pii.PiiDetectorComposite;
import com.njydsz.common.docs.security.redact.DocumentRedactor;
import com.njydsz.common.docs.security.scanner.DocumentSecurityScannerComposite;
import com.njydsz.common.docs.security.watermark.WatermarkProvider;
import com.njydsz.common.docs.summary.DocumentSummarizer;

import lombok.extern.slf4j.Slf4j;

/**
 * 文档处理统一服务门面
 * <p>
 * 整合文档解析、预处理、安全扫描和 PII 检测能力，对外提供一站式 API。
 *
 * <p><b>核心方法：</b>
 * <ul>
 *   <li>{@link #parse} - 解析文档内容</li>
 *   <li>{@link #preprocess} - 预处理文档内容</li>
 *   <li>{@link #scanSecurity} - 安全扫描</li>
 *   <li>{@link #detectPii} - PII 检测</li>
 *   <li>{@link #parseAndPreprocess} - 解析 + 预处理一体化</li>
 *   <li>{@link #convert} - 文档格式转换</li>
 *   <li>{@link #addWatermark} - 添加水印</li>
 *   <li>{@link #redact} - PII 脱敏</li>
 *   <li>{@link #ocrScan} - OCR 识别</li>
 *   <li>{@link #summarize} - 文档摘要</li>
 *   <li>{@link #extractKeywords} - 关键词提取</li>
 *   <li>{@link #parseWithSecurityCheck} - 解析 + 安全扫描一体化</li>
 *   <li>{@link #parseAndRedact} - 解析 + PII 检测 + 脱敏一体化</li>
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
    private final DocumentSecurityScannerComposite securityScanner;
    private final PiiDetectorComposite piiDetector;
    private final DocsProperties properties;
    private final ObjectProvider<DocumentConverter> converterProvider;
    private final ObjectProvider<WatermarkProvider> watermarkProvider;
    private final ObjectProvider<DocumentRedactor> redactorProvider;
    private final ObjectProvider<OcrProvider> ocrProvider;
    private final ObjectProvider<DocumentSummarizer> summarizerProvider;
    private final ObjectProvider<DocsMetrics> metricsProvider;

    public DocumentService(
            DocumentParserRegistry parserRegistry,
            PreprocessPipeline preprocessPipeline,
            DocumentSecurityScannerComposite securityScanner,
            PiiDetectorComposite piiDetector,
            DocsProperties properties,
            ObjectProvider<DocumentConverter> converterProvider,
            ObjectProvider<WatermarkProvider> watermarkProvider,
            ObjectProvider<DocumentRedactor> redactorProvider,
            ObjectProvider<OcrProvider> ocrProvider,
            ObjectProvider<DocumentSummarizer> summarizerProvider,
            ObjectProvider<DocsMetrics> metricsProvider) {
        this.parserRegistry = parserRegistry;
        this.preprocessPipeline = preprocessPipeline;
        this.securityScanner = securityScanner;
        this.piiDetector = piiDetector;
        this.properties = properties;
        this.converterProvider = converterProvider;
        this.watermarkProvider = watermarkProvider;
        this.redactorProvider = redactorProvider;
        this.ocrProvider = ocrProvider;
        this.summarizerProvider = summarizerProvider;
        this.metricsProvider = metricsProvider;
    }

    /**
     * 解析文档
     *
     * @param inputStream 文档输入流
     * @param fileName    文件名
     * @param options     解析选项
     * @return 解析结果
     */
    public DocumentParseResult parse(InputStream inputStream, String fileName, ParseOptions options) {
        Instant start = Instant.now();
        DocumentFormat format = DocumentFormat.UNKNOWN;
        try {
            format = DocumentFormat.fromFileName(fileName);
            if (format == DocumentFormat.UNKNOWN) {
                throw new DocumentException(DocumentExceptionCode.UNSUPPORTED_FORMAT,
                        "无法识别文档格式: " + fileName);
            }

            if (format == DocumentFormat.DOC || format == DocumentFormat.PPT) {
                throw new DocumentException(DocumentExceptionCode.UNSUPPORTED_FORMAT,
                        "旧格式 ." + format.getExtension() + " 建议转换为新格式后再上传");
            }

            if (!parserRegistry.isSupported(format)) {
                throw new DocumentException(DocumentExceptionCode.UNSUPPORTED_FORMAT,
                        "暂不支持解析此格式: " + format);
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
     * @param fileName    文件名
     * @param options     解析选项
     * @return 解析并预处理后的结果
     */
    public DocumentParseResult parseAndPreprocess(InputStream inputStream, String fileName, ParseOptions options) {
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
     * @param fileName    文件名
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
        SecurityScanResult result = securityScanner.scan(inputStream, fileName, format);
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
        List<PiiFinding> findings = piiDetector.detectAll(content);
        recordPiiMetric(findings);
        return findings;
    }

    public byte[] convert(InputStream inputStream, String fileName, DocumentFormat targetFormat) {
        DocumentConverter converter = converterProvider.getIfAvailable();
        if (converter == null) {
            throw new DocumentException(DocumentExceptionCode.CONVERT_FAILED, "文档转换器未注册");
        }
        DocumentFormat sourceFormat = DocumentFormat.fromFileName(fileName);
        return converter.convert(inputStream, fileName, sourceFormat, targetFormat);
    }

    public byte[] addWatermark(InputStream inputStream, String fileName, String watermarkText) {
        if (!properties.isWatermarkEnabled()) {
            throw new DocumentException(DocumentExceptionCode.WATERMARK_FAILED, "水印功能已禁用");
        }
        WatermarkProvider provider = watermarkProvider.getIfAvailable();
        if (provider == null) {
            throw new DocumentException(DocumentExceptionCode.WATERMARK_FAILED, "水印提供者未注册");
        }
        DocumentFormat format = DocumentFormat.fromFileName(fileName);
        return provider.addWatermark(inputStream, fileName, format, watermarkText);
    }

    public byte[] redact(InputStream inputStream, String fileName, List<PiiFinding> findings) {
        if (!properties.isRedactEnabled()) {
            throw new DocumentException(DocumentExceptionCode.TEXT_REDACT_FAILED, "脱敏功能已禁用");
        }
        DocumentRedactor redactor = redactorProvider.getIfAvailable();
        if (redactor == null) {
            throw new DocumentException(DocumentExceptionCode.TEXT_REDACT_FAILED, "脱敏器未注册");
        }
        DocumentFormat format = DocumentFormat.fromFileName(fileName);
        if (!redactor.supports(format)) {
            throw new DocumentException(DocumentExceptionCode.TEXT_REDACT_FAILED,
                    "脱敏器不支持此格式: " + format);
        }
        return redactor.redact(inputStream, fileName, format, findings);
    }

    public DocumentContent ocrScan(InputStream inputStream, String fileName, OcrEngine engine) {
        OcrProvider provider = ocrProvider.getIfAvailable();
        if (provider == null) {
            throw new DocumentException(DocumentExceptionCode.PARSE_FAILED, "OCR 提供者未注册");
        }
        return provider.ocrScan(inputStream, fileName, engine);
    }

    public String summarize(DocumentContent content) {
        DocumentSummarizer summarizer = summarizerProvider.getIfAvailable();
        if (summarizer == null) {
            return "";
        }
        return summarizer.summarize(content);
    }

    public List<String> extractKeywords(DocumentContent content) {
        DocumentSummarizer summarizer = summarizerProvider.getIfAvailable();
        if (summarizer == null) {
            return List.of();
        }
        return summarizer.extractKeywords(content);
    }

    /**
     * 解析 + 安全扫描一体化
     * <p>
     * 高风险时可阻止返回。
     *
     * @param inputStream 文档输入流
     * @param fileName    文件名
     * @param options     解析选项
     * @return 解析结果（高风险时 success=false）
     */
    public DocumentParseResult parseWithSecurityCheck(InputStream inputStream, String fileName, ParseOptions options) {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("ydsz-docs-sec-", ".tmp");
            inputStream.transferTo(Files.newOutputStream(tempFile));

            SecurityScanResult scanResult = scanSecurity(Files.newInputStream(tempFile), fileName);
            if (properties.isBlockOnHighRisk()
                    && scanResult.getSecurityLevel() != null
                    && scanResult.getSecurityLevel().ordinal() >= SecurityLevel.HIGH.ordinal()) {
                return DocumentParseResult.builder()
                        .success(false)
                        .errorMessage("high risk")
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
            deleteTempFile(tempFile);
        }
    }

    /**
     * 解析 + PII 检测 + 脱敏一体化
     *
     * @param inputStream 文档输入流
     * @param fileName    文件名
     * @param options     解析选项
     * @return 脱敏后的文档字节流
     */
    public byte[] parseAndRedact(InputStream inputStream, String fileName, ParseOptions options) {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("ydsz-docs-redact-", ".tmp");
            inputStream.transferTo(Files.newOutputStream(tempFile));

            DocumentParseResult parseResult = parse(Files.newInputStream(tempFile), fileName, options);
            if (!parseResult.isSuccess() || parseResult.getContent() == null) {
                return Files.readAllBytes(tempFile);
            }
            List<PiiFinding> findings = detectPii(parseResult.getContent());
            if (findings.isEmpty()) {
                return Files.readAllBytes(tempFile);
            }
            return redact(Files.newInputStream(tempFile), fileName, findings);
        } catch (DocumentException e) {
            throw e;
        } catch (Exception e) {
            throw new DocumentException(DocumentExceptionCode.TEXT_REDACT_FAILED, e);
        } finally {
            deleteTempFile(tempFile);
        }
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
                    .collect(java.util.stream.Collectors.groupingBy(PiiFinding::getType))
                    .forEach((type, list) -> metrics.recordPiiDetected(type, list.size()));
        }
    }

    private void recordSecurityScanMetric(SecurityLevel level) {
        DocsMetrics metrics = metricsProvider.getIfAvailable();
        if (metrics != null && level != null) {
            metrics.recordSecurityScan(level);
        }
    }

    private void deleteTempFile(Path tempFile) {
        if (tempFile != null) {
            try {
                Files.deleteIfExists(tempFile);
            } catch (Exception ignored) {
                // 临时文件删除失败不影响主流程
            }
        }
    }
}
