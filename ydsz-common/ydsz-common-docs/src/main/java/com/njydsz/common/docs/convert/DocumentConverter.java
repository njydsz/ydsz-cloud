package com.njydsz.common.docs.convert;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import com.njydsz.common.docs.domain.DocumentContent;
import com.njydsz.common.docs.domain.ParseOptions;
import com.njydsz.common.docs.enums.DocumentFormat;
import com.njydsz.common.docs.exception.DocumentException;
import com.njydsz.common.docs.exception.DocumentExceptionCode;
import com.njydsz.common.docs.parser.impl.PdfDocumentParser;

/**
 * 文档格式转换器
 * <p>
 * 将 Office 文档（Word/Excel/PPT）和 PDF 转换为纯文本格式。
 * <p>
 * P2 功能：当前仅支持 Office→TXT 和 PDF→TXT 转换，
 * Office→PDF 转换需要 LibreOffice/OpenOffice 服务，留作后续扩展。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnClass(name = "org.apache.poi.xwpf.usermodel.XWPFDocument")
public class DocumentConverter {

    private final PdfDocumentParser pdfParser;

    public DocumentConverter(PdfDocumentParser pdfParser) {
        this.pdfParser = pdfParser;
    }

    /**
     * 转换文档格式
     *
     * @param inputStream   原始文档输入流
     * @param fileName      原始文件名
     * @param sourceFormat  源格式
     * @param targetFormat  目标格式
     * @return 转换后的文档字节流
     */
    public byte[] convert(InputStream inputStream, String fileName,
                          DocumentFormat sourceFormat, DocumentFormat targetFormat) {
        if (targetFormat == DocumentFormat.TXT) {
            return convertToText(inputStream, fileName, sourceFormat);
        }
        throw new DocumentException(DocumentExceptionCode.CONVERT_FAILED,
                "不支持的目标格式: " + targetFormat + "（当前仅支持转换为 TXT）");
    }

    /**
     * 将 Office 文档转换为纯文本
     */
    private byte[] convertToText(InputStream inputStream, String fileName, DocumentFormat sourceFormat) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {

            switch (sourceFormat) {
                case DOCX -> convertWordToText(inputStream, writer);
                case XLSX, XLS -> convertExcelToText(inputStream, writer);
                case PDF -> convertPdfToText(inputStream, writer);
                case PPTX -> convertPptToText(inputStream, writer);
                default -> throw new DocumentException(DocumentExceptionCode.CONVERT_FAILED,
                        "不支持的源格式: " + sourceFormat);
            }

            writer.flush();
            return output.toByteArray();

        } catch (IOException e) {
            log.error("[DocumentConverter] 转换失败: {}", fileName, e);
            throw new DocumentException(DocumentExceptionCode.CONVERT_FAILED, e);
        }
    }

    /**
     * Word → 纯文本
     */
    private void convertWordToText(InputStream input, Writer writer) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(input)) {
            for (XWPFParagraph para : doc.getParagraphs()) {
                String text = para.getText();
                if (text != null && !text.isBlank()) {
                    writer.write(text);
                    writer.write('\n');
                }
            }
        }
    }

    /**
     * Excel → 纯文本
     */
    private void convertExcelToText(InputStream input, Writer writer) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(input)) {
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                writer.write("=== ");
                writer.write(sheet.getSheetName());
                writer.write(" ===\n");
                for (Row row : sheet) {
                    StringBuilder sb = new StringBuilder();
                    for (int c = 0; c < row.getLastCellNum(); c++) {
                        if (c > 0) {
                            sb.append('\t');
                        }
                        Cell cell = row.getCell(c);
                        if (cell != null) {
                            sb.append(getCellValueAsString(cell));
                        }
                    }
                    if (!sb.isEmpty()) {
                        writer.write(sb.toString());
                        writer.write('\n');
                    }
                }
            }
        }
    }

    /**
     * PPT → 纯文本
     */
    private void convertPptToText(InputStream input, Writer writer) throws IOException {
        try (XMLSlideShow ppt = new XMLSlideShow(input)) {
            int pageNum = 0;
            for (XSLFSlide slide : ppt.getSlides()) {
                pageNum++;
                writer.write("--- Slide ");
                writer.write(String.valueOf(pageNum));
                writer.write(" ---\n");
                for (var shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        String text = textShape.getText();
                        if (text != null && !text.isBlank()) {
                            writer.write(text);
                            writer.write('\n');
                        }
                    }
                }
            }
        }
    }

    /**
     * PDF → 纯文本（委托 PdfDocumentParser）
     */
    private void convertPdfToText(InputStream input, Writer writer) throws IOException {
        DocumentContent content = pdfParser.parse(input, "convert.pdf", ParseOptions.builder().build());
        writer.write(content.getText());
    }

    /**
     * 将单元格值转换为字符串（公共方法，供 DocumentConverter 和 ExcelDocumentParser 共享）
     */
    public static String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }
        CellType cellType = cell.getCellType();
        return switch (cellType) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                            .format(cell.getLocalDateTimeCellValue());
                }
                double num = cell.getNumericCellValue();
                if (num == Math.floor(num)) {
                    yield String.valueOf((long) num);
                }
                yield String.valueOf(num);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue();
                } catch (Exception e) {
                    yield String.valueOf(cell.getNumericCellValue());
                }
            }
            default -> "";
        };
    }
}
