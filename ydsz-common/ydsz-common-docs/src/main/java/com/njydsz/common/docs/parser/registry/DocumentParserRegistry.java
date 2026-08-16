package com.njydsz.common.docs.parser.registry;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.common.docs.enums.DocumentFormat;
import com.njydsz.common.docs.exception.DocumentException;
import com.njydsz.common.docs.exception.DocumentExceptionCode;
import com.njydsz.common.docs.parser.DocumentParser;

/**
 * 文档解析器注册表
 *
 * <p>负责解析器的注册、查找和路由。所有 {@link DocumentParser} 实现类通过 Spring 自动注入注册到此注册表。
 *
 * <p><b>线程安全性：</b>使用 {@link ConcurrentHashMap} 存储注册表，线程安全。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class DocumentParserRegistry {

  /** 格式到解析器的映射 */
  private final Map<DocumentFormat, DocumentParser> parserMap = new ConcurrentHashMap<>();

  /**
   * 通过 Spring 构造器注入所有 {@link DocumentParser} Bean
   *
   * @param parsers Spring 容器中所有 DocumentParser 实现
   */
  public DocumentParserRegistry(List<DocumentParser> parsers) {
    parsers = parsers != null ? parsers : List.of();
    if (!parsers.isEmpty()) {
      for (DocumentParser parser : parsers) {
        register(parser);
      }
      log.info(
          "[DocumentParserRegistry] 已注册 {} 个文档解析器: {}",
          parsers.size(),
          parsers.stream().map(p -> p.getSupportedFormat().name()).toList());
    }
  }

  /**
   * 注册解析器
   *
   * @param parser 文档解析器
   */
  public void register(DocumentParser parser) {
    if (parser == null) {
      return;
    }
    // 注册主格式
    registerFormat(parser.getSupportedFormat(), parser);
    // 注册 supports() 支持但 getSupportedFormat() 未覆盖的额外格式
    for (DocumentFormat format : DocumentFormat.values()) {
      if (format != parser.getSupportedFormat() && parser.supports(format)) {
        registerFormat(format, parser);
      }
    }
  }

  private void registerFormat(DocumentFormat format, DocumentParser parser) {
    DocumentParser existing = parserMap.put(format, parser);
    if (existing != null && existing != parser) {
      log.warn(
          "[DocumentParserRegistry] 解析器覆盖注册: format={}, old={}, new={}",
          format,
          existing.getClass().getSimpleName(),
          parser.getClass().getSimpleName());
    }
  }

  /**
   * 根据格式获取解析器
   *
   * @param format 文档格式
   * @return 对应的解析器
   * @throws DocumentException 如果找不到对应格式的解析器
   */
  public DocumentParser getParser(DocumentFormat format) {
    DocumentParser parser = parserMap.get(format);
    if (parser == null) {
      throw new DocumentException(DocumentExceptionCode.UNSUPPORTED_FORMAT, "不支持的文档格式: " + format);
    }
    return parser;
  }

  /**
   * 检查是否支持指定格式
   *
   * @param format 文档格式
   * @return 如果有对应的解析器返回 true
   */
  public boolean isSupported(DocumentFormat format) {
    return parserMap.containsKey(format);
  }

  /**
   * 获取所有已注册的格式
   *
   * @return 已注册格式列表
   */
  public Set<DocumentFormat> getSupportedFormats() {
    return Collections.unmodifiableSet(parserMap.keySet());
  }
}
