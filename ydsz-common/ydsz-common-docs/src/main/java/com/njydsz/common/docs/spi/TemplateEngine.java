package com.njydsz.common.docs.spi;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

/**
 * 模板生成引擎 SPI。
 *
 * <p>定义基于模板的文档生成能力，使业务模块能够：
 * <ul>
 *   <li>使用 Word 模板（.docx，基于 Apache POI / docx4j / POI-TL）生成合同、报告等 Word 文件</li>
 *   <li>使用 HTML 模板渲染为 PDF（基于 Flying Saucer / OpenPDF / wkhtmltopdf）</li>
 *   <li>统一模板加载与变量替换的编程接口，避免业务代码直接绑定特定模板引擎</li>
 * </ul>
 *
 * <p>分层：
 * <table border="1">
 *   <tr><th>能力</th><th>Word 生成</th><th>PDF 生成</th></tr>
 *   <tr><td>底层依赖</td><td>Apache POI / POI-TL / docx4j</td><td>Flying Saucer / OpenPDF</td></tr>
 *   <tr><td>模板格式</td><td>.docx（含占位符 ${var}）</td><td>HTML / Thymeleaf 模板</td></tr>
 *   <tr><td>调用方式</td><td>{@link #renderWordTemplate}</td><td>{@link #renderHtmlToPdf}</td></tr>
 * </table>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * @Service
 * public class ContractService {
 *     private final TemplateEngine templateEngine;
 *     public byte[] generateContract(String templateName, Map<String, Object> vars) throws IOException {
 *         try (InputStream template = loadTemplate(templateName);
 *              ByteArrayOutputStream out = new ByteArrayOutputStream()) {
 *             templateEngine.renderWordTemplate(template, out, vars);
 *             return out.toByteArray();
 *         }
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.25
 * @see PreviewRenderer
 */
public interface TemplateEngine {

  /**
   * 渲染 Word 模板并输出到指定流。
   *
   * <p>模板文件应为 .docx 格式，占位符使用 {@code ${variableName}} 语法；
   * 支持表格循环（表格行内使用 {@code {{items}}} 标记）和图片占位符（{@code ${imageField}} 替换为字节数组）。
   *
   * @param templateStream Word 模板输入流（非 null；调用方负责关闭）
   * @param output 输出流（非 null；调用方负责关闭）
   * @param variables 模板变量 Map（key 对应占位符名称，value 为替换值；value 支持 String、Number、byte[]）
   * @throws IOException 模板读取/写入失败或模板格式不正确
   */
  void renderWordTemplate(
      InputStream templateStream, OutputStream output, Map<String, Object> variables)
      throws IOException;

  /**
   * 将 HTML 模板渲染为 PDF。
   *
   * <p>HTML 模板可使用任意模板引擎（Thymeleaf、Freemarker、Velocity）预先渲染为纯 HTML，
   * 再调用本方法将 HTML 转为 PDF 二进制。CSS 支持以 CSS 2.1 为基准（Flying Saucer 限制）。
   *
   * @param htmlStream HTML 输入流（非 null；调用方负责关闭）
   * @param output PDF 输出流（非 null；调用方负责关闭）
   * @param baseUri 用于解析相对路径资源（图片、CSS）的 URI（可为 null）
   * @throws IOException HTML 解析失败或 PDF 生成失败
   */
  void renderHtmlToPdf(InputStream htmlStream, OutputStream output, String baseUri)
      throws IOException;

  /**
   * 检查指定模板类型是否被当前引擎支持。
   *
   * @param templateType 模板类型（"WORD"/"HTML"/"MARKDOWN"）
   * @return true 表示支持该类型的渲染
   */
  boolean supports(String templateType);
}
