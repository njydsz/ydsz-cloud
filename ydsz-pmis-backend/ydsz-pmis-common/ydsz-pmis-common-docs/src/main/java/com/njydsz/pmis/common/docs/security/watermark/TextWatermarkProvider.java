package com.njydsz.pmis.common.docs.security.watermark;

import com.njydsz.pmis.common.docs.enums.DocumentFormat;
import com.njydsz.pmis.common.docs.exception.DocumentException;
import com.njydsz.pmis.common.docs.exception.DocumentExceptionCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * PDF 文本水印提供者
 * <p>
 * 为 PDF 文档添加对角线文本水印（如用户名、时间戳），用于泄露追溯。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 1.0.0
 * @since 1.3.0
 */
@Slf4j
@Component
@ConditionalOnClass(name = "org.apache.pdfbox.Loader")
public class TextWatermarkProvider implements WatermarkProvider {

    /** 水印字体大小 */
    private static final float FONT_SIZE = 60f;

    /** 水印透明度 */
    private static final float ALPHA = 0.3f;

    /** 水印旋转角度（弧度） */
    private static final double ROTATION = Math.toRadians(45);

    @Override
    public byte[] addWatermark(InputStream inputStream, String fileName, DocumentFormat format, String watermarkText) {
        if (format != DocumentFormat.PDF) {
            throw new DocumentException(DocumentExceptionCode.WATERMARK_FAILED,
                    "文本水印目前仅支持 PDF 格式，不支持: " + format);
        }

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("pmis-docs-watermark-", ".pdf");
            inputStream.transferTo(Files.newOutputStream(tempFile));

            try (PDDocument document = Loader.loadPDF(tempFile.toFile());
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {

                PDFont font = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

                for (PDPage page : document.getPages()) {
                    try (PDPageContentStream contentStream = new PDPageContentStream(
                            document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {

                        float pageWidth = page.getMediaBox().getWidth();
                        float pageHeight = page.getMediaBox().getHeight();

                        // 设置透明度
                        com.fasterxml.jackson.databind.ObjectMapper mapper = null; // placeholder, remove
                        var extState = new org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState();
                        extState.setNonStrokingAlphaConstant(ALPHA);
                        extState.setStrokingAlphaConstant(ALPHA);
                        contentStream.setGraphicsStateParameters(extState);

                        // 计算居中位置
                        float textWidth = font.getStringWidth(watermarkText) / 1000 * FONT_SIZE;
                        float x = (pageWidth - textWidth * (float) Math.cos(ROTATION)) / 2;
                        float y = (pageHeight - textWidth * (float) Math.sin(ROTATION)) / 2;

                        contentStream.beginText();
                        contentStream.setFont(font, FONT_SIZE);
                        contentStream.setTextMatrix(AffineTransform.getRotateInstance(ROTATION, x, y));
                        contentStream.setNonStrokingColor(Color.GRAY);
                        contentStream.showText(watermarkText);
                        contentStream.endText();
                    }
                }

                document.save(output);
                return output.toByteArray();

            }
        } catch (IOException e) {
            log.error("[TextWatermarkProvider] 水印添加失败: {}", fileName, e);
            throw new DocumentException(DocumentExceptionCode.WATERMARK_FAILED, e);
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
    public boolean supports(DocumentFormat format) {
        return format == DocumentFormat.PDF;
    }
}
