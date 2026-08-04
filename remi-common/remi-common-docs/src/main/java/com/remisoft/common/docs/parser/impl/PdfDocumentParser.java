package com.remisoft.common.docs.parser.impl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import com.remisoft.common.docs.domain.DocumentContent;
import com.remisoft.common.docs.domain.DocumentMetadata;
import com.remisoft.common.docs.domain.DocumentSection;
import com.remisoft.common.docs.domain.ParseOptions;
import com.remisoft.common.docs.enums.DocumentFormat;
import com.remisoft.common.docs.enums.ParseMode;
import com.remisoft.common.docs.exception.DocumentException;
import com.remisoft.common.docs.exception.DocumentExceptionCode;
import com.remisoft.common.docs.parser.DocumentParser;

import lombok.extern.slf4j.Slf4j;

/**
 * PDF 文档解析器
 * <p>
 * 基于 Apache PDFBox 3.x 解析 PDF 文档，提取纯文本、页码信息和元数据。
 *
 * <p><b>大文件处理：</b>使用临时文件方式加载，避免内存溢出。
 * 当 {@link ParseMode#FAST} 模式时跳过图片提取。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnClass(name = "org.apache.pdfbox.Loader")
public class PdfDocumentParser implements DocumentParser {

    /**
     * 逐页抽取 PDF 文本，并按需读取文档属性作为元数据。
     *
     * <p><b>先落临时文件再加载：</b>PDFBox 3.x 从 {@link java.io.File} 加载时可对
     * 对象流做随机访问与按需换页，内存占用远低于从 {@code InputStream} 全量载入，
     * 这是应对大 PDF 的关键手段。临时文件在 {@code finally} 中必删，
     * 删除失败仅静默忽略（交由操作系统临时目录清理策略兜底），不影响解析结果。
     *
     * <p><b>逐页而非整篇抽取：</b>为每页单独设置 {@link PDFTextStripper} 的起止页，
     * 目的是让每段文本都能携带准确的 {@code pageNumber}，
     * 供后续 PII 定位、安全审计精确回溯到具体页码。代价是文本抽取要跑 N 遍页面遍历。
     *
     * <p><b>加密文档快速失败：</b>检测到加密立即抛出，不尝试空密码解密。
     * 因为静默解密可能绕过文档所有者设定的访问控制，属于安全风险。
     *
     * <p>{@code maxPages} 大于 0 时截断超出部分，此时返回的 {@code totalPages}
     * 是被截断后的页数，而元数据中的 {@code pageCount} 仍为 PDF 真实总页数，两者<b>可能不等</b>。
     * 图片抽取尚未实现，{@code images} 恒为空列表。
     *
     * @param inputStream PDF 字节流，由调用方负责关闭；为 {@code null} 时视为空文档
     * @param fileName    原始文件名，在 PDF 未内嵌标题时作为元数据标题的兜底值
     * @param options     解析选项，读取 {@code maxPages}、{@code mode}、{@code extractMetadata}；
     *                    传 {@code null} 时使用全默认选项
     * @return 文档内容，每页非空文本对应一个 paragraph 分节
     * @throws DocumentException 入参流为 {@code null} 时错误码 {@code DOCUMENT_EMPTY}；
     *                           文档受密码保护时错误码 {@code DOCUMENT_ENCRYPTED}；
     *                           临时文件读写或 PDF 结构损坏时错误码 {@code PARSE_FAILED}
     */
    @Override
    public DocumentContent parse(InputStream inputStream, String fileName, ParseOptions options) {
        if (inputStream == null) {
            throw new DocumentException(DocumentExceptionCode.DOCUMENT_EMPTY);
        }

        ParseOptions opts = options != null ? options : ParseOptions.builder().build();
        Path tempFile = null;

        try {
            // 写入临时文件，PDFBox 3.x 优先从文件加载以减少内存占用
            tempFile = Files.createTempFile("remi-docs-pdf-", ".pdf");
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

    /**
     * 声明本解析器在注册中心占据的格式槽位。
     *
     * @return 恒为 {@link DocumentFormat#PDF}
     */
    @Override
    public DocumentFormat getSupportedFormat() {
        return DocumentFormat.PDF;
    }

    /**
     * 读取 PDF 文档信息字典，组装元数据。
     *
     * <p>在 {@link ParseMode#FAST} 且未显式开启元数据抽取时直接跳过，
     * 只回填文件名标题——快速模式的目标是尽快拿到正文，属性读取属于可省成本。
     *
     * <p>PDF 的信息字典整体可缺失、单字段也可为空，故所有取值均做空值防护：
     * 标题为空时回退文件名，时间为空时置 {@code null} 而不用当前时间伪造。
     * 创建/修改时间按 <b>JVM 默认时区</b>转换，跨时区部署时展示值可能与文档原始时区不一致。
     *
     * @param document 已打开的 PDF 文档，不可为 {@code null}
     * @param fileName 原始文件名，作为标题缺失时的兜底
     * @param opts     解析选项，不可为 {@code null}（调用方已做默认值兜底）
     * @return 元数据对象；快速模式下仅含标题，其余字段为 {@code null}
     */
    private DocumentMetadata extractMetadata(PDDocument document, String fileName, ParseOptions opts) {
        if (opts.getMode() == ParseMode.FAST && !opts.isExtractMetadata()) {
            return DocumentMetadata.builder().title(fileName).build();
        }

        PDDocumentInformation info = document.getDocumentInformation();
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
