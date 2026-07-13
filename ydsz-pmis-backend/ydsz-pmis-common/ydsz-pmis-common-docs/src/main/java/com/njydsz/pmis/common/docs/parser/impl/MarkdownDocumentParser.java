package com.njydsz.pmis.common.docs.parser.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.njydsz.pmis.common.docs.domain.DocumentContent;
import com.njydsz.pmis.common.docs.domain.DocumentMetadata;
import com.njydsz.pmis.common.docs.domain.DocumentSection;
import com.njydsz.pmis.common.docs.domain.ParseOptions;
import com.njydsz.pmis.common.docs.enums.DocumentFormat;
import com.njydsz.pmis.common.docs.exception.DocumentException;
import com.njydsz.pmis.common.docs.exception.DocumentExceptionCode;
import com.njydsz.pmis.common.docs.parser.DocumentParser;

import lombok.extern.slf4j.Slf4j;

/**
 * Markdown 文档解析器
 * <p>
 * 解析 Markdown 文件，提取标题层级、段落、列表等结构化内容。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 1.0.0
 * @since 1.3.0
 */
@Slf4j
@Component
public class MarkdownDocumentParser implements DocumentParser {

    /** Markdown 标题正则 */
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$");

    /** Markdown 列表项正则 */
    private static final Pattern LIST_PATTERN = Pattern.compile("^[*\\-+]\\s+(.+)$");

    /** Markdown 有序列表项正则 */
    private static final Pattern ORDERED_LIST_PATTERN = Pattern.compile("^\\d+\\.\\s+(.+)$");

    @Override
    public DocumentContent parse(InputStream inputStream, String fileName, ParseOptions options) {
        if (inputStream == null) {
            throw new DocumentException(DocumentExceptionCode.DOCUMENT_EMPTY);
        }

        List<DocumentSection> sections = new ArrayList<>();
        StringBuilder fullText = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();

                // 标题
                Matcher headingMatcher = HEADING_PATTERN.matcher(trimmed);
                if (headingMatcher.matches()) {
                    int level = headingMatcher.group(1).length();
                    String text = headingMatcher.group(2).trim();
                    sections.add(DocumentSection.builder()
                            .type("heading")
                            .headingLevel(level)
                            .content(text)
                            .pageNumber(1)
                            .build());
                    fullText.append(text).append('\n');
                    continue;
                }

                // 无序列表
                Matcher listMatcher = LIST_PATTERN.matcher(trimmed);
                if (listMatcher.matches()) {
                    String text = listMatcher.group(1).trim();
                    sections.add(DocumentSection.builder()
                            .type("list")
                            .content(text)
                            .pageNumber(1)
                            .build());
                    fullText.append("- ").append(text).append('\n');
                    continue;
                }

                // 有序列表
                Matcher orderedListMatcher = ORDERED_LIST_PATTERN.matcher(trimmed);
                if (orderedListMatcher.matches()) {
                    String text = orderedListMatcher.group(1).trim();
                    sections.add(DocumentSection.builder()
                            .type("list")
                            .content(text)
                            .pageNumber(1)
                            .build());
                    fullText.append(text).append('\n');
                    continue;
                }

                // 普通段落
                if (!trimmed.isEmpty()) {
                    sections.add(DocumentSection.builder()
                            .type("paragraph")
                            .content(trimmed)
                            .pageNumber(1)
                            .build());
                    fullText.append(trimmed).append('\n');
                }
            }
        } catch (IOException e) {
            log.error("[MarkdownDocumentParser] 解析失败: {}", fileName, e);
            throw new DocumentException(DocumentExceptionCode.PARSE_FAILED, e);
        }

        String text = fullText.toString();
        return DocumentContent.builder()
                .text(text)
                .sections(sections)
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
        return DocumentFormat.MARKDOWN;
    }
}
