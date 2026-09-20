package com.njydsz.common.docs.convert;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import com.njydsz.common.docs.domain.DocumentContent;
import com.njydsz.common.docs.domain.ParseOptions;
import com.njydsz.common.docs.enums.DocumentFormat;
import com.njydsz.common.docs.exception.DocumentException;
import com.njydsz.common.docs.exception.DocumentExceptionCode;
import com.njydsz.common.docs.parser.registry.DocumentParserRegistry;

/**
 * 文档格式转换器
 *
 * <p>将 Office 文档（Word/Excel/PPT）和 PDF 转换为纯文本格式。
 *
 * <p>实现策略：优先委托已注册的 {@link DocumentParser} 获取结构化内容后提取文本。
 *
 * <p>注意：本转换器要求相关 {@link DocumentParser} 已注册且对应依赖存在于 classpath，
 * 不支持无解析器时的降级转换（避免与 ExcelFacade 产生重复实现）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
  // CHECKSTYLE.OFF: RegexpSinglelineJava — 字符串常量（注解/反射类名），非代码引用
@ConditionalOnClass(name = "org.apache.poi.xwpf.usermodel.XWPFDocument")
  // CHECKSTYLE.ON: RegexpSinglelineJava
public class DocumentConverter {

  private final DocumentParserRegistry parserRegistry;

  public DocumentConverter(DocumentParserRegistry parserRegistry) {
    this.parserRegistry = parserRegistry;
  }

  /**
   * 转换文档格式
   *
   * @param inputStream 原始文档输入流
   * @param fileName 原始文件名
   * @param sourceFormat 源格式
   * @param targetFormat 目标格式
   * @return 转换后的文档字节流
   */
  public byte[] convert(
      InputStream inputStream,
      String fileName,
      DocumentFormat sourceFormat,
      DocumentFormat targetFormat) {
    if (targetFormat == DocumentFormat.TXT) {
      return convertToText(inputStream, fileName, sourceFormat);
    }
    throw new DocumentException(
        DocumentExceptionCode.CONVERT_FAILED,
        "不支持的目标格式: " + targetFormat + "（当前仅支持转换为 TXT）");
  }

  /**
   * 将文档转换为纯文本字节流。
   *
   * <p>优先使用已注册的解析器获取 {@link DocumentContent#getText()}。
   *
   * @throws DocumentException 解析器不可用时抛出 {@link DocumentExceptionCode#CONVERT_FAILED}
   */
  private byte[] convertToText(
      InputStream inputStream, String fileName, DocumentFormat sourceFormat) {
    if (parserRegistry.isSupported(sourceFormat)) {
      try {
        var parser = parserRegistry.getParser(sourceFormat);
        DocumentContent content =
            parser.parse(inputStream, fileName, ParseOptions.builder().build());
        String text = content.getText();
        return text != null ? text.getBytes(StandardCharsets.UTF_8) : new byte[0];
      } catch (Exception e) {
        log.warn("[DocumentConverter] 解析器委托失败: {}", e.getMessage());
      }
    }

    throw new DocumentException(
        DocumentExceptionCode.CONVERT_FAILED,
        "文档转换器无法处理源格式: " + sourceFormat
            + "（需确保对应解析器已注册且相关依赖存在）");
  }
}
