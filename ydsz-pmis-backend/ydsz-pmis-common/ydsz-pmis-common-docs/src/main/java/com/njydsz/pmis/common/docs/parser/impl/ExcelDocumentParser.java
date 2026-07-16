package com.njydsz.pmis.common.docs.parser.impl;

import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import com.njydsz.pmis.common.docs.domain.DocumentContent;
import com.njydsz.pmis.common.docs.domain.DocumentMetadata;
import com.njydsz.pmis.common.docs.domain.DocumentSection;
import com.njydsz.pmis.common.docs.domain.DocumentTable;
import com.njydsz.pmis.common.docs.domain.ParseOptions;
import com.njydsz.pmis.common.docs.enums.DocumentFormat;
import com.njydsz.pmis.common.docs.exception.DocumentException;
import com.njydsz.pmis.common.docs.exception.DocumentExceptionCode;
import com.njydsz.pmis.common.docs.parser.DocumentParser;

import lombok.extern.slf4j.Slf4j;

/**
 * Excel 文档解析器（.xlsx / .xls）
 * <p>
 * 基于 Apache POI 解析 Excel 文档，提取所有 Sheet 的表格数据。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Slf4j
@Component
@ConditionalOnClass(name = "org.apache.poi.ss.usermodel.WorkbookFactory")
public class ExcelDocumentParser implements DocumentParser {

    @Override
    public DocumentContent parse(InputStream inputStream, String fileName, ParseOptions options) {
        if (inputStream == null) {
            throw new DocumentException(DocumentExceptionCode.DOCUMENT_EMPTY);
        }

        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            List<DocumentTable> tables = new ArrayList<>();
            List<DocumentSection> sections = new ArrayList<>();
            StringBuilder fullText = new StringBuilder();

            int sheetCount = workbook.getNumberOfSheets();
            for (int s = 0; s < sheetCount; s++) {
                Sheet sheet = workbook.getSheetAt(s);
                String sheetName = sheet.getSheetName();

                List<List<String>> rows = new ArrayList<>();
                int maxCols = 0;

                for (Row row : sheet) {
                    List<String> cells = new ArrayList<>();
                    int lastCol = row.getLastCellNum();
                    maxCols = Math.max(maxCols, lastCol);

                    for (int c = 0; c < lastCol; c++) {
                        Cell cell = row.getCell(c);
                        cells.add(getCellValueAsString(cell));
                    }
                    // 过滤空行
                    if (cells.stream().anyMatch(v -> v != null && !v.isBlank())) {
                        rows.add(cells);
                    }
                }

                if (!rows.isEmpty()) {
                    tables.add(DocumentTable.builder()
                            .caption(sheetName)
                            .pageNumber(s + 1)
                            .rowCount(rows.size())
                            .colCount(maxCols)
                            .rows(rows)
                            .build());

                    // 追加文本
                    fullText.append("=== ").append(sheetName).append(" ===\n");
                    for (List<String> row : rows) {
                        fullText.append(String.join("\t", row)).append('\n');
                    }
                    fullText.append('\n');
                }
            }

            String text = fullText.toString();
            return DocumentContent.builder()
                    .text(text)
                    .sections(sections)
                    .tables(tables)
                    .metadata(DocumentMetadata.builder()
                            .title(fileName)
                            .charCount(text.length())
                            .build())
                    .totalChars(text.length())
                    .totalPages(sheetCount)
                    .build();

        } catch (IOException e) {
            log.error("[ExcelDocumentParser] 解析失败: {}", fileName, e);
            throw new DocumentException(DocumentExceptionCode.PARSE_FAILED, e);
        }
    }

    @Override
    public DocumentFormat getSupportedFormat() {
        return DocumentFormat.XLSX;
    }

    /**
     * 将单元格值转换为字符串
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }
        CellType cellType = cell.getCellType();
        return switch (cellType) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(cell.getLocalDateTimeCellValue());
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
