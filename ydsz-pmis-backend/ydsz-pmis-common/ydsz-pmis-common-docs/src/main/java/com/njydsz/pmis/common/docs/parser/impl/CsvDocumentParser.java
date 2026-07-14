package com.njydsz.pmis.common.docs.parser.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
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
 * CSV 文档解析器
 * <p>
 * 解析 CSV 文件，将其转换为结构化表格和文本内容。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 * @since 1.3.0
 */
@Slf4j
@Component
@ConditionalOnClass(name = "org.apache.commons.csv.CSVParser")
public class CsvDocumentParser implements DocumentParser {

    @Override
    public DocumentContent parse(InputStream inputStream, String fileName, ParseOptions options) {
        if (inputStream == null) {
            throw new DocumentException(DocumentExceptionCode.DOCUMENT_EMPTY);
        }

        Charset charset = resolveCharset(options);
        List<List<String>> rows = new ArrayList<>();
        StringBuilder fullText = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, charset));
             CSVParser parser = CSVFormat.DEFAULT.parse(reader)) {

            int colCount = 0;
            for (CSVRecord record : parser) {
                List<String> row = new ArrayList<>();
                for (int i = 0; i < record.size(); i++) {
                    row.add(record.get(i));
                }
                rows.add(row);
                colCount = Math.max(colCount, record.size());
            }
        } catch (IOException e) {
            log.error("[CsvDocumentParser] 解析失败: {}", fileName, e);
            throw new DocumentException(DocumentExceptionCode.PARSE_FAILED, e);
        }

        // 构建纯文本
        for (List<String> row : rows) {
            fullText.append(String.join("\t", row)).append('\n');
        }

        String text = fullText.toString();
        DocumentTable table = DocumentTable.builder()
                .caption(fileName)
                .pageNumber(1)
                .rowCount(rows.size())
                .colCount(rows.isEmpty() ? 0 : rows.get(0).size())
                .rows(rows)
                .build();

        return DocumentContent.builder()
                .text(text)
                .sections(List.of(DocumentSection.builder()
                        .type("table")
                        .content(text)
                        .pageNumber(1)
                        .build()))
                .tables(List.of(table))
                .metadata(DocumentMetadata.builder()
                        .title(fileName)
                        .charCount(text.length())
                        .build())
                .totalChars(text.length())
                .totalPages(1)
                .build();
    }

    @Override
    public DocumentFormat getSupportedFormat() {
        return DocumentFormat.CSV;
    }

    private Charset resolveCharset(ParseOptions options) {
        if (options != null && options.getCharset() != null && !options.getCharset().isBlank()) {
            return Charset.forName(options.getCharset());
        }
        return StandardCharsets.UTF_8;
    }
}
