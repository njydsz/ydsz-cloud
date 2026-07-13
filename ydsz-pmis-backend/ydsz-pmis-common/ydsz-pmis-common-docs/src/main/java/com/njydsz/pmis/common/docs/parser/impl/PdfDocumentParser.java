package com.njydsz.pmis.common.docs.parser.impl;

import com.njydsz.pmis.common.docs.domain.DocumentContent;
import com.njydsz.pmis.common.docs.domain.DocumentImage;
import com.njydsz.pmis.common.docs.domain.DocumentMetadata;
import com.njydsz.pmis.common.docs.domain.DocumentSection;
import com.njydsz.pmis.common.docs.domain.ParseOptions;
import com.njydsz.pmis.common.docs.enums.DocumentFormat;
import com.njydsz.pmis.common.docs.enums.ParseMode;
import com.njydsz.pmis.common.docs.exception.DocumentException;
import com.njydsz.pmis.common.docs.exception.DocumentExceptionCode;
import com.njydsz.pmis.common.docs.parser.DocumentParser;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF 文档解析器
 * <p>
 * 基于 Apache PDFBox 3.x 解析 PDF 文档，提取纯文本、页码信息和元数据。
 *
 * <p><b>大文件处理：</b>使用临时文件方式加载，避免内存溢出。
 * 当 {@link ParseMode#FAST} 模式时跳过图片提取。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 1.0.0
 * @since 1.3.0
 */
@Slf4j
@Component
@ConditionalOnClass(name = "org.apache.pdfbox.Loader")
public class PdfDocumentParser implements DocumentParser {

    @Override
    public DocumentContent parse(InputStream inputStream, String fileName, ParseOptions options) {
        if (inputStream == null) {
            throw new DocumentException(DocumentExceptionCode.DOCUMENT_EMPTY);
        }

        ParseOptions opts = options != null ? options : ParseOptions.builder().build();
        Path tempFile = null;

        try {
            // 写入临时文件，PDFBox 3.x 优先从文件加载以减少内存占用
            tempFile = Files.createTempFile("pmis-docs-pdf-", ".pdf");
            inputStream.transferTo(Files.newOutputStream(tempFile));

            PDDocument document = Loader.loadPDF(tempFile.toFile());

            // 检查加密
            if (document.isEncrypted()) {
                document.close();
                throw new DocumentException(DocumentExceptionCode.DOCUMENT_ENCRYPTED,
                        "PDF 文档已加密，需要密码: " + fileName);
            }

            int pageCount = document.getNumberOfPages();

            // 页数限制
            if (opts.getMaxPages() > 0 && pageCount > opts.getMaxPages()) {
                pageCount = opts.getMaxPages();
            }

            PDFTextStripper stripper = new PDFTextStripper();

            List<DocumentSection> sections = new ArrayList<>();
            StringBuilder fullText = new StringBuilder();

            for (int i = 0; i < pageCount; i++) {
                stripper.setStartPage(i + 1);
                stripper.setEndPage(i + 1);
                String pageText = stripper.getText(document);

                if (pageText != null && !pageText.isBlank()) {
                    sections.add(DocumentSection.builder()
                            .type("paragraph")
                            .content(pageText.trim())
                            .pageNumber(i + 1)
                            .build());
                    fullText.append(pageText);
                }
            }

            // 提取元数据
            DocumentMetadata metadata = extractMetadata(document, fileName, opts);

            document.close();
            String text = fullText.toString();

            return DocumentContent.builder()
                    .text(text)
                    .sections(sections)
                    .images(List.of())
                    .metadata(metadata)
                    .totalChars(text.length())
                    .totalPages(pageCount)
                    .build();

        } catch (DocumentException e) {
            throw e;
        } catch (IOException e) {
            log.error("[PdfDocumentParser] 解析失败: {}", fileName, e);
            throw new DocumentException(DocumentExceptionCode.PARSE_FAILED, e);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                    // 临时文件删除失败不影响主流程
                }
            }
        }
    }

    @Override
    public DocumentFormat getSupportedFormat() {
        return DocumentFormat.PDF;
    }

    /**
     * 提取 PDF 元数据
     */
    private DocumentMetadata extractMetadata(PDDocument document, String fileName, ParseOptions opts) {
        if (opts.getMode() == ParseMode.FAST && !opts.isExtractMetadata()) {
            return DocumentMetadata.builder().title(fileName).build();
        }

        org.apache.pdfbox.pdmodel.PDDocumentInformation info = document.getDocumentInformation();
        DocumentMetadata.DocumentMetadataBuilder builder = DocumentMetadata.builder()
                .title(info != null && info.getTitle() != null ? info.getTitle() : fileName)
                .pageCount(document.getNumberOfPages());

        if (info != null) {
            builder.author(info.getAuthor())
                    .subject(info.getSubject())
                    .keywords(info.getKeywords())
                    .creator(info.getCreator())
                    .producer(info.getProducer())
                    .createdTime(info.getCreationDate() != null ? info.getCreationDate().toInstant()
                            .atZone(ZoneId.systemDefault()).toLocalDateTime() : null)
                    .modifiedTime(info.getModificationDate() != null ? info.getModificationDate().toInstant()
                            .atZone(ZoneId.systemDefault()).toLocalDateTime() : null);
        }

        return builder.build();
    }
}
