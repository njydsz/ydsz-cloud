package com.njydsz.pmis.common.docs.parser.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableCell;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
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
 * PowerPoint 文档解析器（.pptx）
 * <p>
 * 基于 Apache POI XSLF 解析 PowerPoint 文档，提取每页幻灯片的文本内容和表格。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 1.0.0
 * @since 1.3.0
 */
@Slf4j
@Component
@ConditionalOnClass(name = "org.apache.poi.xslf.usermodel.XMLSlideShow")
public class PptDocumentParser implements DocumentParser {

    @Override
    public DocumentContent parse(InputStream inputStream, String fileName, ParseOptions options) {
        if (inputStream == null) {
            throw new DocumentException(DocumentExceptionCode.DOCUMENT_EMPTY);
        }

        try (XMLSlideShow ppt = new XMLSlideShow(inputStream)) {
            List<DocumentSection> sections = new ArrayList<>();
            List<DocumentTable> tables = new ArrayList<>();
            StringBuilder fullText = new StringBuilder();

            List<XSLFSlide> slides = ppt.getSlides();
            int slideCount = slides.size();

            for (int i = 0; i < slideCount; i++) {
                XSLFSlide slide = slides.get(i);
                int pageNum = i + 1;

                for (XSLFShape shape : slide.getShapes()) {
                    // 文本形状
                    if (shape instanceof XSLFTextShape textShape) {
                        String text = textShape.getText();
                        if (text != null && !text.isBlank()) {
                            sections.add(DocumentSection.builder()
                                    .type("paragraph")
                                    .content(text.trim())
                                    .pageNumber(pageNum)
                                    .build());
                            fullText.append(text).append('\n');
                        }
                    }
                    // 表格形状
                    else if (shape instanceof XSLFTable tableShape) {
                        List<List<String>> rows = new ArrayList<>();
                        int numRows = tableShape.getNumberOfRows();
                        int maxCols = 0;
                        for (int r = 0; r < numRows; r++) {
                            List<String> cells = new ArrayList<>();
                            int numCols = tableShape.getNumberOfColumns();
                            maxCols = Math.max(maxCols, numCols);
                            for (int c = 0; c < numCols; c++) {
                                XSLFTableCell cell = tableShape.getCell(r, c);
                                cells.add(cell != null ? cell.getText() : "");
                            }
                            rows.add(cells);
                        }
                        if (!rows.isEmpty()) {
                            tables.add(DocumentTable.builder()
                                    .pageNumber(pageNum)
                                    .rowCount(rows.size())
                                    .colCount(maxCols)
                                    .rows(rows)
                                    .build());
                        }
                    }
                }
            }

            String text = fullText.toString();
            return DocumentContent.builder()
                    .text(text)
                    .sections(sections)
                    .tables(tables)
                    .metadata(DocumentMetadata.builder()
                            .title(fileName)
                            .pageCount(slideCount)
                            .charCount(text.length())
                            .build())
                    .totalChars(text.length())
                    .totalPages(slideCount)
                    .build();

        } catch (IOException e) {
            log.error("[PptDocumentParser] 解析失败: {}", fileName, e);
            throw new DocumentException(DocumentExceptionCode.PARSE_FAILED, e);
        }
    }

    @Override
    public DocumentFormat getSupportedFormat() {
        return DocumentFormat.PPTX;
    }
}
