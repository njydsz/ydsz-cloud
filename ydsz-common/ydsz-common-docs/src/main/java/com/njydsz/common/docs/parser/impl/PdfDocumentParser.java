package com.njydsz.common.docs.parser.impl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import com.njydsz.common.docs.domain.DocumentContent;
import com.njydsz.common.docs.domain.DocumentMetadata;
import com.njydsz.common.docs.domain.DocumentSection;
import com.njydsz.common.docs.domain.ParseOptions;
import com.njydsz.common.docs.enums.DocumentFormat;
import com.njydsz.common.docs.enums.ParseMode;
import com.njydsz.common.docs.enums.ParseProfile;
import com.njydsz.common.docs.exception.DocumentException;
import com.njydsz.common.docs.exception.DocumentExceptionCode;
import com.njydsz.common.docs.parser.DocumentParser;
import com.njydsz.common.util.io.TempFileManager;

/**
 * PDF 文档解析器
 *
 * <p>基于 Apache PDFBox 3.x 解析 PDF 文档，提取纯文本、页码信息和元数据。
 *
 * <p><b>大文件处理：</b>使用临时文件方式加载，避免内存溢出。 当 {@link ParseMode#FAST} 模式时跳过图片提取。
 *
 * <p><b>流式解析：</b>通过 {@link #parseStreaming} 方法支持逐页回调， 适用于超大 PDF 的增量处理场景（如全文索引构建），避免一次性载入全文。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
  // CHECKSTYLE.OFF: RegexpSinglelineJava — 字符串常量（注解/反射类名），非代码引用
@ConditionalOnClass(name = "org.apache.pdfbox.Loader")
  // CHECKSTYLE.ON: RegexpSinglelineJava
public class PdfDocumentParser implements DocumentParser {

  private final TempFileManager tempFileManager;

  public PdfDocumentParser(TempFileManager tempFileManager) {
    this.tempFileManager = tempFileManager;
  }

  /**
   * 逐页抽取 PDF 文本，并按需读取文档属性作为元数据。
   *
   * <p><b>先落临时文件再加载：</b>PDFBox 3.x 从 {@link java.io.File} 加载时可对 对象流做随机访问与按需换页，内存占用远低于从 {@code
   * InputStream} 全量载入， 这是应对大 PDF 的关键手段。临时文件在 {@code finally} 中必删，
   * 删除失败仅静默忽略（交由操作系统临时目录清理策略兜底），不影响解析结果。
   *
   * <p><b>单次全量抽取 + 换页符分割：</b>一次性调用 {@link PDFTextStripper#getText} 抽取全部文本， 再按 PDFBox 输出的换页符 {@code
   * \f} 分割为各页内容，使每段文本仍携带准确的 {@code pageNumber}， 供后续 PII 定位、安全审计精确回溯到具体页码。相比逐页遍历，性能从 O(N) 抽取降至 O(1)
   * 抽取。
   *
   * <p><b>加密文档快速失败：</b>检测到加密立即抛出，不尝试空密码解密。 因为静默解密可能绕过文档所有者设定的访问控制，属于安全风险。
   *
   * <p>{@code maxPages} 大于 0 时截断超出部分，此时返回的 {@code totalPages} 是被截断后的页数，而元数据中的 {@code pageCount} 仍为
   * PDF 真实总页数，两者<b>可能不等</b>。 图片抽取尚未实现，{@code images} 恒为空列表。
   *
   * <p><b>输出轮廓影响：</b>
   *
   * <ul>
   *   <li>{@link ParseProfile#TEXT_ONLY} - 仅填充 {@code text}，{@code sections} 为空
   *   <li>{@link ParseProfile#STRUCTURED} - 填充 {@code text}、{@code sections}、{@code metadata}
   *   <li>{@link ParseProfile#FULL} - 完整输出（图片除外，尚未实现）
   * </ul>
   *
   * @param inputStream PDF 字节流，由调用方负责关闭；为 {@code null} 时视为空文档
   * @param fileName 原始文件名，在 PDF 未内嵌标题时作为元数据标题的兜底值
   * @param options 解析选项，读取 {@code maxPages}、{@code mode}、{@code profile}、{@code extractMetadata}； 传
   *     {@code null} 时使用全默认选项
   * @return 文档内容，每页非空文本对应一个 paragraph 分节
   * @throws DocumentException 入参流为 {@code null} 时错误码 {@code DOCUMENT_EMPTY}； 文档受密码保护时错误码 {@code
   *     DOCUMENT_ENCRYPTED}； 临时文件读写或 PDF 结构损坏时错误码 {@code PARSE_FAILED}
   */
  @Override
  public DocumentContent parse(InputStream inputStream, String fileName, ParseOptions options) {
    if (inputStream == null) {
      throw new DocumentException(DocumentExceptionCode.DOCUMENT_EMPTY);
    }

    ParseOptions opts = options != null ? options : ParseOptions.builder().build();
    ParseProfile profile = opts.getProfile() != null ? opts.getProfile() : ParseProfile.STRUCTURED;
    Path tempFile = null;

    try {
      // 写入临时文件，PDFBox 3.x 优先从文件加载以减少内存占用
      tempFile = tempFileManager.createAndWrite("ydsz-docs-pdf-", ".pdf", inputStream);

      PDDocument document = Loader.loadPDF(tempFile.toFile());

      // 检查加密
      if (document.isEncrypted()) {
        document.close();
        throw new DocumentException(
            DocumentExceptionCode.DOCUMENT_ENCRYPTED, "PDF 文档已加密，需要密码: " + fileName);
      }

      int pageCount = document.getNumberOfPages();

      // 页数限制
      if (opts.getMaxPages() > 0 && pageCount > opts.getMaxPages()) {
        pageCount = opts.getMaxPages();
      }

      PDFTextStripper stripper = new PDFTextStripper();
      stripper.setStartPage(1);
      stripper.setEndPage(pageCount);

      String fullText = stripper.getText(document);

      // 根据 profile 决定输出结构化程度
      List<DocumentSection> sections = new ArrayList<>();
      if (profile != ParseProfile.TEXT_ONLY) {
        // 单次全量抽取后按换页符 \f 分割，保留页码信息（O(1) 抽取替代 O(N) 遍历）
        if (fullText != null && !fullText.isEmpty()) {
          String[] pageTexts = fullText.split("\f", -1);
          for (int i = 0; i < pageTexts.length && i < pageCount; i++) {
            String pageText = pageTexts[i];
            if (pageText != null && !pageText.isBlank()) {
              sections.add(
                  DocumentSection.builder()
                      .type("paragraph")
                      .content(pageText.trim())
                      .pageNumber(i + 1)
                      .build());
            }
          }
        }
      }

      // 提取元数据（TEXT_ONLY 模式可跳过）
      DocumentMetadata metadata = null;
      if (profile != ParseProfile.TEXT_ONLY) {
        metadata = extractMetadata(document, fileName, opts);
      }

      document.close();

      return DocumentContent.builder()
          .text(fullText)
          .sections(sections)
          .images(List.of())
          .metadata(metadata)
          .totalChars(fullText != null ? fullText.length() : 0)
          .totalPages(pageCount)
          .build();

    } catch (DocumentException e) {
      throw e;
    } catch (IOException e) {
      log.error("[PdfDocumentParser] 解析失败: {}", fileName, e);
      throw new DocumentException(DocumentExceptionCode.PARSE_FAILED, e);
    } finally {
      tempFileManager.deleteTracked(tempFile);
    }
  }

  /**
   * 流式解析 PDF：逐页提取文本并通过回调消费，避免一次性全文载入内存。
   *
   * <p>适用于超大 PDF 的增量处理场景（如全文索引构建、分页 PII 扫描）。 回调接收的 {@link PageContent} 包含页码与文本，_consumer_ 可将内容即时推送到
   * 下游（消息队列、索引器），无需等待全文解析完成。
   *
   * <p><b>与 {@link #parse} 的差异：</b>
   *
   * <ul>
   *   <li>不返回完整 {@link DocumentContent}，通过回调增量输出
   *   <li>内存占用恒定为单页大小，与文档总页数无关
   *   <li>不读取元数据（流式场景无需）
   * </ul>
   *
   * <p><b>异常处理：</b>单页抽取失败不会中断后续页面，失败页返回空文本并记录日志。
   *
   * @param inputStream PDF 字节流，由调用方负责关闭；为 {@code null} 时直接返回不回调
   * @param fileName 原始文件名，用于日志记录
   * @param consumer 每页内容的消费者，接收 {@link PageContent}；不为 {@code null}
   * @throws DocumentException 文档加密或文件损坏时抛出 {@code PARSE_FAILED}
   */
  public void parseStreaming(
      InputStream inputStream, String fileName, Consumer<PageContent> consumer) {
    if (inputStream == null || consumer == null) {
      return;
    }

    Path tempFile = null;
    try {
      tempFile = tempFileManager.createAndWrite("ydsz-docs-pdf-stream-", ".pdf", inputStream);

      PDDocument document = Loader.loadPDF(tempFile.toFile());

      if (document.isEncrypted()) {
        document.close();
        throw new DocumentException(
            DocumentExceptionCode.DOCUMENT_ENCRYPTED, "PDF 文档已加密: " + fileName);
      }

      int pageCount = document.getNumberOfPages();
      PDFTextStripper stripper = new PDFTextStripper();

      for (int i = 0; i < pageCount; i++) {
        try {
          stripper.setStartPage(i + 1);
          stripper.setEndPage(i + 1);
          String pageText = stripper.getText(document);
          if (pageText != null && !pageText.isBlank()) {
            consumer.accept(new PageContent(i + 1, pageText.trim()));
          }
        } catch (Exception e) {
          log.warn("[PdfDocumentParser] 第 {} 页流式抽取失败: {}", i + 1, e.getMessage());
          // 单页失败不中断后续页面
        }
      }

      document.close();
    } catch (DocumentException e) {
      throw e;
    } catch (Exception e) {
      log.error("[PdfDocumentParser] 流式解析失败: {}", fileName, e);
      throw new DocumentException(DocumentExceptionCode.PARSE_FAILED, e);
    } finally {
      tempFileManager.deleteTracked(tempFile);
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
   * <p>在 {@link ParseMode#FAST} 且未显式开启元数据抽取时直接跳过， 只回填文件名标题——快速模式的目标是尽快拿到正文，属性读取属于可省成本。
   *
   * <p>PDF 的信息字典整体可缺失、单字段也可为空，故所有取值均做空值防护： 标题为空时回退文件名，时间为空时置 {@code null} 而不用当前时间伪造。 创建/修改时间按
   * <b>JVM 默认时区</b>转换，跨时区部署时展示值可能与文档原始时区不一致。
   *
   * @param document 已打开的 PDF 文档，不可为 {@code null}
   * @param fileName 原始文件名，作为标题缺失时的兜底
   * @param opts 解析选项，不可为 {@code null}（调用方已做默认值兜底）
   * @return 元数据对象；快速模式下仅含标题，其余字段为 {@code null}
   */
  private DocumentMetadata extractMetadata(
      PDDocument document, String fileName, ParseOptions opts) {
    if (opts.getMode() == ParseMode.FAST && !opts.isExtractMetadata()) {
      return DocumentMetadata.builder().title(fileName).build();
    }

    PDDocumentInformation info = document.getDocumentInformation();
    DocumentMetadata.DocumentMetadataBuilder builder =
        DocumentMetadata.builder()
            .title(info != null && info.getTitle() != null ? info.getTitle() : fileName)
            .pageCount(document.getNumberOfPages());

    if (info != null) {
      builder
          .author(info.getAuthor())
          .subject(info.getSubject())
          .keywords(info.getKeywords())
          .creator(info.getCreator())
          .producer(info.getProducer())
          .createdTime(
              info.getCreationDate() != null
                  ? info.getCreationDate()
                      .toInstant()
                      .atZone(ZoneId.systemDefault())
                      .toLocalDateTime()
                  : null)
          .modifiedTime(
              info.getModificationDate() != null
                  ? info.getModificationDate()
                      .toInstant()
                      .atZone(ZoneId.systemDefault())
                      .toLocalDateTime()
                  : null);
    }

    return builder.build();
  }

  /**
   * 流式解析单页内容。
   *
   * @param pageNumber 页码（从 1 开始）
   * @param text 该页提取的文本
   */
  public record PageContent(int pageNumber, String text) {}
}
