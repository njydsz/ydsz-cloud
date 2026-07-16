package com.njydsz.common.docs.security.scanner;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionJavaScript;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import com.njydsz.common.docs.domain.SecurityScanResult;
import com.njydsz.common.docs.enums.DocumentFormat;
import com.njydsz.common.docs.enums.SecurityLevel;

import lombok.extern.slf4j.Slf4j;

/**
 * PDF 安全检测器
 * <p>
 * 检测 PDF 文档中的 JavaScript 脚本、嵌入文件和可疑外部链接。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnClass(name = "org.apache.pdfbox.Loader")
public class PdfJsDetector implements DocumentSecurityScanner {

    private final com.njydsz.common.docs.config.DocsProperties properties;

    public PdfJsDetector(com.njydsz.common.docs.config.DocsProperties properties) {
        this.properties = properties;
    }

    @Override
    public SecurityScanResult scan(InputStream inputStream, String fileName, DocumentFormat format) {
        if (format != DocumentFormat.PDF) {
            return SecurityScanResult.builder()
                    .securityLevel(SecurityLevel.SAFE)
                    .findings(List.of())
                    .success(true)
                    .build();
        }

        List<SecurityScanResult.SecurityFinding> findings = new ArrayList<>();
        Path tempFile = null;

        try {
            tempFile = Files.createTempFile("pmis-docs-pdfscan-", ".pdf");
            inputStream.transferTo(Files.newOutputStream(tempFile));

            try (PDDocument document = Loader.loadPDF(tempFile.toFile())) {
                PDDocumentCatalog catalog = document.getDocumentCatalog();

                // 1. 检测 OpenAction 中的 JavaScript
                var openAction = catalog.getOpenAction();
                if (openAction instanceof PDActionJavaScript) {
                    findings.add(SecurityScanResult.SecurityFinding.builder()
                            .type("pdf_js")
                            .description("检测到 PDF OpenAction 中的 JavaScript 脚本")
                            .location("Document Catalog / OpenAction")
                            .level(SecurityLevel.MEDIUM)
                            .build());
                }

                // 2. 检测嵌入文件
                PDDocumentNameDictionary names = catalog.getNames();
                if (names != null && names.getEmbeddedFiles() != null) {
                    var embeddedFiles = names.getEmbeddedFiles();
                    if (embeddedFiles.getNames() != null && !embeddedFiles.getNames().isEmpty()) {
                        findings.add(SecurityScanResult.SecurityFinding.builder()
                                .type("embedded_object")
                                .description("检测到 " + embeddedFiles.getNames().size() + " 个嵌入文件")
                                .location("Document Catalog / EmbeddedFiles")
                                .level(SecurityLevel.MEDIUM)
                                .build());
                    }
                }

                // 3. 检测每页中的可疑链接和 JavaScript
                int maxScan = properties.getSecurityMaxScanPages();
                int pageCount = maxScan > 0 ? Math.min(document.getNumberOfPages(), maxScan) : document.getNumberOfPages();
                for (int i = 0; i < pageCount; i++) {
                    var page = document.getPage(i);
                    if (page == null) {
                        continue;
                    }
                    List<PDAnnotation> annotations = page.getAnnotations();
                    if (annotations == null) {
                        continue;
                    }
                    for (PDAnnotation ann : annotations) {
                        if (ann instanceof PDAnnotationLink link) {
                            var action = link.getAction();
                            if (action instanceof PDActionURI uriAction) {
                                String uri = uriAction.getURI();
                                if (uri != null && isSuspiciousUri(uri)) {
                                    findings.add(SecurityScanResult.SecurityFinding.builder()
                                            .type("external_link")
                                            .description("检测到可疑外部链接: " + uri)
                                            .location("第 " + (i + 1) + " 页")
                                            .level(SecurityLevel.LOW)
                                            .build());
                                }
                            } else if (action instanceof PDActionJavaScript) {
                                findings.add(SecurityScanResult.SecurityFinding.builder()
                                        .type("pdf_js")
                                        .description("检测到页面注解中的 JavaScript 脚本")
                                        .location("第 " + (i + 1) + " 页 / 注解")
                                        .level(SecurityLevel.MEDIUM)
                                        .build());
                            }
                        }
                    }
                }

            }
        } catch (IOException e) {
            log.warn("[PdfJsDetector] PDF 安全扫描失败: {}", fileName, e);
            return SecurityScanResult.builder()
                    .securityLevel(SecurityLevel.SAFE)
                    .findings(List.of())
                    .success(false)
                    .errorMessage("PDF 解析失败: " + e.getMessage())
                    .build();
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                    // 临时文件删除失败不影响主流程
                }
            }
        }

        SecurityLevel level = findings.isEmpty() ? SecurityLevel.SAFE
                : findings.stream().map(SecurityScanResult.SecurityFinding::getLevel)
                        .max(Enum::compareTo).orElse(SecurityLevel.SAFE);

        return SecurityScanResult.builder()
                .securityLevel(level)
                .findings(findings)
                .success(true)
                .build();
    }

    /**
     * 判断 URI 是否可疑（非 HTTPS 或可疑域名）
     */
    private boolean isSuspiciousUri(String uri) {
        if (uri == null || uri.isBlank()) {
            return false;
        }
        // 非 http/https 协议
        if (!uri.startsWith("http://") && !uri.startsWith("https://")) {
            return true;
        }
        // HTTP 协议（非加密）
        return uri.startsWith("http://");
    }

    @Override
    public String getName() {
        return "pdf-js-detector";
    }
}
