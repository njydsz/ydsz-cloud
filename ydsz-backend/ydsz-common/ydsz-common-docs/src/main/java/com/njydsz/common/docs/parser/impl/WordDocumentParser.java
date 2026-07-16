package com.njydsz.common.docs.parser.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import com.njydsz.common.docs.domain.DocumentContent;
import com.njydsz.common.docs.domain.DocumentMetadata;
import com.njydsz.common.docs.domain.DocumentSection;
import com.njydsz.common.docs.domain.DocumentTable;
import com.njydsz.common.docs.domain.ParseOptions;
import com.njydsz.common.docs.enums.DocumentFormat;
import com.njydsz.common.docs.exception.DocumentException;
import com.njydsz.common.docs.exception.DocumentExceptionCode;
import com.njydsz.common.docs.parser.DocumentParser;

import lombok.extern.slf4j.Slf4j;

/**
 * Word 文档解析器（.docx）
 * <p>
 * 基于 Apache POI XWPF 解析 Word OOXML 文档，提取段落、标题层级和表格。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnClass(name = "org.apache.poi.xwpf.usermodel.XWPFDocument")
public class WordDocumentParser implements DocumentParser {

    @Override
    public DocumentContent parse(InputStream inputStream, String fileName, ParseOptions options) {
        if (inputStream == null) {
            throw new DocumentException(DocumentExceptionCode.DOCUMENT_EMPTY);
        }

        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            List<DocumentSection> sections = new ArrayList<>();
            List<DocumentTable> tables = new ArrayList<>();
            StringBuilder fullText = new StringBuilder();

            // 解析段落
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String text = paragraph.getText();
                if (text == null || text.isBlank()) {
                    continue;
                }

                String style = paragraph.getStyle();
                int headingLevel = extractHeadingLevel(style);

                sections.add(DocumentSection.builder()
                        .type(headingLevel > 0 ? "heading" : "paragraph")
                        .headingLevel(headingLevel > 0 ? headingLevel : null)
                        .content(text.trim())
                        .pageNumber(1)
                        .build());
                fullText.append(text).append('\n');
            }

            // 解析表格
            if (options == null || options.isExtractTables()) {
                for (XWPFTable table : document.getTables()) {
                    List<List<String>> rows = new ArrayList<>();
                    for (XWPFTableRow row : table.getRows()) {
                        List<String> cells = new ArrayList<>();
                        for (XWPFTableCell cell : row.getTableCells()) {
                            cells.add(cell.getText() != null ? cell.getText().trim() : "");
                        }
                        rows.add(cells);
                    }
                    if (!rows.isEmpty()) {
                        tables.add(DocumentTable.builder()
                                .pageNumber(1)
                                .rowCount(rows.size())
                                .colCount(rows.get(0).size())
                                .rows(rows)
                                .build());
                    }
                }
            }

            String text = fullText.toString();
            return DocumentContent.builder()
                    .text(text)
                    .sections(sections)
                    .tables(tables)
                    .images(List.of())
                    .metadata(DocumentMetadata.builder()
                            .title(fileName)
                            .charCount(text.length())
                            .build())
                    .totalChars(text.length())
                    .totalPages(1)
                    .build();

        } catch (IOException e) {
            log.error("[WordDocumentParser] 解析失败: {}", fileName, e);
            throw new DocumentException(DocumentExceptionCode.PARSE_FAILED, e);
        }
    }

    @Override
    public DocumentFormat getSupportedFormat() {
        return DocumentFormat.DOCX;
    }

    /**
     * 从段落样式名中提取标题层级
     */
    private int extractHeadingLevel(String style) {
        if (style == null) {
            return 0;
        }
        if (style.startsWith("Heading") || style.startsWith("heading")) {
            String numPart = style.replaceAll("[^0-9]", "");
            try {
                int level = Integer.parseInt(numPart);
                return (level >= 1 && level <= 9) ? level : 0;
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }
}
