package com.njydsz.pmis.common.docs.service;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import com.njydsz.pmis.common.docs.domain.DocumentContent;
import com.njydsz.pmis.common.docs.domain.DocumentParseResult;
import com.njydsz.pmis.common.docs.domain.ParseOptions;
import com.njydsz.pmis.common.docs.domain.PiiFinding;
import com.njydsz.pmis.common.docs.domain.SecurityScanResult;
import com.njydsz.pmis.common.docs.enums.DocumentFormat;
import com.njydsz.pmis.common.docs.exception.DocumentException;
import com.njydsz.pmis.common.docs.exception.DocumentExceptionCode;
import com.njydsz.pmis.common.docs.parser.registry.DocumentParserRegistry;
import com.njydsz.pmis.common.docs.preprocess.pipeline.PreprocessPipeline;
import com.njydsz.pmis.common.docs.security.pii.PiiDetectorComposite;
import com.njydsz.pmis.common.docs.security.scanner.DocumentSecurityScannerComposite;

import lombok.RequiredArgsConstructor;
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
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 * @since 1.3.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentParserRegistry parserRegistry;
    private final PreprocessPipeline preprocessPipeline;
    private final DocumentSecurityScannerComposite securityScanner;
    private final PiiDetectorComposite piiDetector;

    /**
     * 解析文档
     *
     * @param inputStream 文档输入流
     * @param fileName     文件名
     * @param options      解析选项
     * @return 解析结果
     */
    public DocumentParseResult parse(InputStream inputStream, String fileName, ParseOptions options) {
        Instant start = Instant.now();
        try {
            DocumentFormat format = DocumentFormat.fromFileName(fileName);
            if (format == DocumentFormat.UNKNOWN) {
                throw new DocumentException(DocumentExceptionCode.UNSUPPORTED_FORMAT,
                        "无法识别文档格式: " + fileName);
            }

            if (!parserRegistry.isSupported(format)) {
                throw new DocumentException(DocumentExceptionCode.UNSUPPORTED_FORMAT,
                        "暂不支持解析此格式: " + format);
            }

            var parser = parserRegistry.getParser(format);
            DocumentContent content = parser.parse(inputStream, fileName, options);

            return DocumentParseResult.builder()
                    .content(content)
                    .elapsed(Duration.between(start, Instant.now()))
                    .success(true)
                    .fileName(fileName)
                    .build();

        } catch (DocumentException e) {
            return DocumentParseResult.builder()
                    .elapsed(Duration.between(start, Instant.now()))
                    .success(false)
                    .errorMessage(e.getMessage())
                    .fileName(fileName)
                    .build();
        } catch (Exception e) {
            log.error("[DocumentService] 解析异常: {}", fileName, e);
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
        return preprocessPipeline.execute(content);
    }

    /**
     * 解析 + 预处理一体化
     *
     * @param inputStream 文档输入流
     * @param fileName     文件名
     * @param options      解析选项
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
     * @param fileName     文件名
     * @return 安全扫描结果
     */
    public SecurityScanResult scanSecurity(InputStream inputStream, String fileName) {
        DocumentFormat format = DocumentFormat.fromFileName(fileName);
        return securityScanner.scan(inputStream, fileName, format);
    }

    /**
     * PII 检测
     *
     * @param content 文档内容
     * @return PII 发现列表
     */
    public List<PiiFinding> detectPii(DocumentContent content) {
        return piiDetector.detectAll(content);
    }
}
