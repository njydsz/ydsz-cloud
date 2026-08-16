package com.njydsz.common.docs.security.watermark;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import com.njydsz.common.docs.config.DocsProperties;
import com.njydsz.common.docs.enums.DocumentFormat;
import com.njydsz.common.docs.exception.DocumentException;
import com.njydsz.common.docs.exception.DocumentExceptionCode;

/**
 * PDF 文本水印提供者
 * <p>
 * 为 PDF 文档添加对角线文本水印（如用户名、时间戳），用于泄露追溯。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnClass(name = "org.apache.pdfbox.Loader")
public class TextWatermarkProvider implements WatermarkProvider {

    private final DocsProperties properties;

    public TextWatermarkProvider(DocsProperties properties) {
        this.properties = properties;
    }

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
            tempFile = Files.createTempFile("ydsz-docs-watermark-", ".pdf");
            inputStream.transferTo(Files.newOutputStream(tempFile));

            try (PDDocument document = Loader.loadPDF(tempFile.toFile());
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {

                PDFont font = loadFont(document);
                if (font == null) {
                    font = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                }

                for (PDPage page : document.getPages()) {
                    try (PDPageContentStream contentStream = new PDPageContentStream(
                            document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {

                        float pageWidth = page.getMediaBox().getWidth();
                        float pageHeight = page.getMediaBox().getHeight();

                        // 设置透明度
                        PDExtendedGraphicsState extState = new PDExtendedGraphicsState();
                        extState.setNonStrokingAlphaConstant(ALPHA);
                        extState.setStrokingAlphaConstant(ALPHA);
                        contentStream.setGraphicsStateParameters(extState);

                        // 计算居中位置
                        float textWidth = font.getStringWidth(watermarkText) / 1000 * FONT_SIZE;
                        float x = (pageWidth - textWidth * (float) Math.cos(ROTATION)) / 2;
                        float y = (pageHeight - textWidth * (float) Math.sin(ROTATION)) / 2;

                        // 使用 Matrix 设置旋转和位移
                        float cos = (float) Math.cos(ROTATION);
                        float sin = (float) Math.sin(ROTATION);
                        Matrix matrix = new Matrix(cos, sin, -sin, cos, x, y);

                        contentStream.beginText();
                        contentStream.setFont(font, FONT_SIZE);
                        contentStream.setTextMatrix(matrix);
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


    /**
     * 尝试加载嵌入中文字体，失败时回退到 Helvetica（仅支持 ASCII）
     */
    private PDFont loadFont(PDDocument document) {
        // 优先使用配置的自定义字体
        String configuredPath = properties.getWatermarkFontPath();
        if (configuredPath != null && !configuredPath.isBlank()) {
            try {
                var file = new File(configuredPath);
                if (file.exists()) {
                    return PDType0Font.load(document, file);
                }
            } catch (Exception e) {
                log.warn("[TextWatermarkProvider] 配置字体加载失败: {}", configuredPath);
            }
        }
        // 回退到系统字体目录加载中文字体
        String[] fontPaths = {
            System.getProperty("java.home") + "/lib/fonts/fontconfig",
            "C:/Windows/Fonts/simhei.ttf",
            "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc",
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"
        };
        for (String path : fontPaths) {
            try {
                var file = new File(path);
                if (file.exists()) {
                    return PDType0Font.load(document, file);
                }
            } catch (Exception e) {
                log.debug("[TextWatermarkProvider] 字体加载失败: {}", path);
            }
        }
        log.warn("[TextWatermarkProvider] 未找到中文字体, 回退到 Helvetica（不支持中文水印）");
        return null;
    }
    @Override
    public boolean supports(DocumentFormat format) {
        return format == DocumentFormat.PDF;
    }
}
