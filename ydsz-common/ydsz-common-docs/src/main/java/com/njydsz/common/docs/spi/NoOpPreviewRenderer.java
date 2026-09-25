package com.njydsz.common.docs.spi;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 默认文件预览渲染器（NoOp 实现）。
 *
 * <p>当 classpath 中未引入 PDFBox / LibreOffice / JODConverter 等预览后端时，自动注册本 NoOp 实例，
 * 避免业务模块注入 {@link PreviewRenderer} 时因缺少 Bean 启动失败。
 *
 * <p>所有方法均抛出 {@link UnsupportedOperationException}，提示引入的下游依赖与推荐后端。
 *
 * <p>业务模块需引入以下依赖之一并声明自定义 {@link PreviewRenderer} Bean（标注 {@code @Primary}）替换本默认：
 * <ul>
 *   <li>PDF 预览：{@code org.apache.pdfbox:pdfbox}（3.x 推荐）</li>
 *   <li>Office 预览：{@code org.jodconverter:jodconverter-local} + 本地安装 LibreOffice</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.25
 * @see PreviewRenderer
 */
@Component
@Primary
@ConditionalOnMissingBean(PreviewRenderer.class)
public class NoOpPreviewRenderer implements PreviewRenderer {

  private static final String PDF_HINT =
      "请引入 PDFBox（org.apache.pdfbox:pdfbox）并声明自定义 @Primary PreviewRenderer"
          + " Bean 以启用 PDF 预览渲染。";
  private static final String OFFICE_HINT =
      "请引入 JODConverter（org.jodconverter:jodconverter-local）+ 安装 LibreOffice(soffice)"
          + " 并声明自定义 @Primary PreviewRenderer Bean 以启用 Office 文档预览。";

  @Override
  public List<PageImage> renderPdfToImages(InputStream pdfStream, String outputFormat)
      throws IOException {
    throw new UnsupportedOperationException(PDF_HINT);
  }

  @Override
  public List<PageImage> renderOfficeToImages(InputStream officeStream, String outputFormat)
      throws IOException {
    throw new UnsupportedOperationException(OFFICE_HINT);
  }
}
