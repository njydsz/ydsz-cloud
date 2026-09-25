package com.njydsz.common.docs.spi;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 默认模板生成引擎（NoOp 实现）。
 *
 * <p>当 classpath 中未引入 POI-TL / docx4j / Flying Saucer 等模板后端时，自动注册本 NoOp 实例，
 * 避免业务模块注入 {@link TemplateEngine} 时因缺少 Bean 启动失败。
 *
 * <p>所有方法均抛出 {@link UnsupportedOperationException}，提示引入的下游依赖与推荐后端。
 *
 * <p>业务模块需引入以下依赖之一并声明自定义 {@link TemplateEngine} Bean（标注 {@code @Primary}）替换本默认：
 * <ul>
 *   <li>Word：{@code com.deepoove:poi-tl} 或 {@code org.docx4j:docx4j}</li>
 *   <li>PDF：{@code org.xhtmlrenderer:flying-saucer-pdf} 或 {@code com.github.librepdf:openpdf}</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.25
 * @see TemplateEngine
 */
@Component
@Primary
@ConditionalOnMissingBean(TemplateEngine.class)
public class NoOpTemplateEngine implements TemplateEngine {

  private static final String WORD_HINT =
      "请引入 POI-TL（com.deepoove:poi-tl）或 docx4j（org.docx4j:docx4j）并声明自定义 "
          + "@Primary TemplateEngine Bean 以启用 Word 模板生成。";
  private static final String PDF_HINT =
      "请引入 Flying Saucer（org.xhtmlrenderer:flying-saucer-pdf）或 OpenPDF"
          + "（com.github.librepdf:openpdf）并声明自定义 @Primary TemplateEngine Bean 以启用 HTML→PDF。";

  @Override
  public void renderWordTemplate(
      InputStream templateStream, OutputStream output, Map<String, Object> variables)
      throws IOException {
    throw new UnsupportedOperationException(WORD_HINT);
  }

  @Override
  public void renderHtmlToPdf(InputStream htmlStream, OutputStream output, String baseUri)
      throws IOException {
    throw new UnsupportedOperationException(PDF_HINT);
  }

  @Override
  public boolean supports(String templateType) {
    return false;
  }
}
