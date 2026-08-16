package com.njydsz.common.base.exporter;

import java.io.File;
import java.io.IOException;

/**
 * 文档导出器接口（SPI）
 *
 * <p>定义 API 文档导出的标准规范，作为导出器 SPI 用于支持多种实现（如默认 HTML 导出器、 Markdown 结构化导出器、PDF 导出器等）。任何新增格式只需要实现该接口并注册为
 * Spring Bean 即可。
 *
 * <p>接口提供 {@link #export(String, String, String)} 作为统一入口， {@link #exportToHtml(String,
 * String)}、{@link #exportToMarkdown(String, String)}、 {@link #exportToJson(String, String)}
 * 作为便捷方法，默认委托给 {@link #export}。
 *
 * <p><b>实现要求：</b>
 *
 * <ul>
 *   <li>实现类应保证线程安全，建议使用无状态设计
 *   <li>{@link #getSupportedFormats()} 返回的格式需在 {@link #isSupportedFormat(String)} 中同样命中
 *   <li>IO 异常应原样上抛，由调用方决定是否重试
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface DocExporter {

  /**
   * 导出为 HTML 格式
   *
   * <p>默认委托给 {@link #export(String, String, String)}，子类可覆盖以优化性能。
   *
   * @param apiDocs OpenAPI 文档 JSON 字符串
   * @param outputDir 输出目录
   * @return 导出的文件对象
   * @throws IOException 如果导出过程中发生 IO 异常
   */
  default File exportToHtml(String apiDocs, String outputDir) throws IOException {
    return export(apiDocs, outputDir, "html");
  }

  /**
   * 导出为 Markdown 格式
   *
   * <p>默认委托给 {@link #export(String, String, String)}，子类可覆盖以优化性能。
   *
   * @param apiDocs OpenAPI 文档 JSON 字符串
   * @param outputDir 输出目录
   * @return 导出的文件对象
   * @throws IOException 如果导出过程中发生 IO 异常
   */
  default File exportToMarkdown(String apiDocs, String outputDir) throws IOException {
    return export(apiDocs, outputDir, "markdown");
  }

  /**
   * 导出为 JSON 格式
   *
   * <p>默认委托给 {@link #export(String, String, String)}，子类可覆盖以优化性能。
   *
   * @param apiDocs OpenAPI 文档 JSON 字符串
   * @param outputDir 输出目录
   * @return 导出的文件对象
   * @throws IOException 如果导出过程中发生 IO 异常
   */
  default File exportToJson(String apiDocs, String outputDir) throws IOException {
    return export(apiDocs, outputDir, "json");
  }

  /**
   * 根据格式类型导出
   *
   * <p>支持的格式包括：{@code html}、{@code markdown} / {@code md}、{@code json}。
   *
   * @param apiDocs OpenAPI 文档 JSON 字符串
   * @param outputDir 输出目录
   * @param format 导出格式（不区分大小写）
   * @return 导出的文件对象
   * @throws IOException 如果导出过程中发生 IO 异常
   * @throws IllegalArgumentException 如果 format 为空或不支持
   */
  File export(String apiDocs, String outputDir, String format) throws IOException;

  /**
   * 检查是否支持指定的导出格式
   *
   * @param format 格式名称（不区分大小写）
   * @return 如果支持返回 true，否则返回 false
   */
  boolean isSupportedFormat(String format);

  /**
   * 获取所有支持的导出格式
   *
   * @return 支持的格式列表（标准名称，如 {@code markdown}、{@code html}）
   */
  String[] getSupportedFormats();
}
