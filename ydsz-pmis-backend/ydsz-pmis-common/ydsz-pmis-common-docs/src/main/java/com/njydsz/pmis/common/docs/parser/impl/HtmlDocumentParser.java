package com.njydsz.pmis.common.docs.parser.impl;

import com.njydsz.pmis.common.docs.domain.DocumentContent;
import com.njydsz.pmis.common.docs.domain.DocumentImage;
import com.njydsz.pmis.common.docs.domain.DocumentMetadata;
import com.njydsz.pmis.common.docs.domain.DocumentSection;
import com.njydsz.pmis.common.docs.domain.DocumentTable;
import com.njydsz.pmis.common.docs.domain.ParseOptions;
import com.njydsz.pmis.common.docs.enums.DocumentFormat;
import com.njydsz.pmis.common.docs.exception.DocumentException;
import com.njydsz.pmis.common.docs.exception.DocumentExceptionCode;
import com.njydsz.pmis.common.docs.parser.DocumentParser;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * HTML 文档解析器
 * <p>
 * 解析 HTML 文件，提取标题、段落、表格、图片等结构化内容。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 1.0.0
 * @since 1.3.0
 */
@Slf4j
@Component
@ConditionalOnClass(name = "org.jsoup.Jsoup")
public class HtmlDocumentParser implements DocumentParser {

    @Override
    public DocumentContent parse(InputStream inputStream, String fileName, ParseOptions options) {
        if (inputStream == null) {
            throw new DocumentException(DocumentExceptionCode.DOCUMENT_EMPTY);
        }

        Document doc;
        try {
            doc = Jsoup.parse(inputStream, null, "");
        } catch (IOException e) {
            log.error("[HtmlDocumentParser] 解析失败: {}", fileName, e);
            throw new DocumentException(DocumentExceptionCode.PARSE_FAILED, e);
        }

        List<DocumentSection> sections = new ArrayList<>();
        List<DocumentTable> tables = new ArrayList<>();
        List<DocumentImage> images = new ArrayList<>();
        StringBuilder fullText = new StringBuilder();

        // 提取标题层级
        for (int i = 1; i <= 6; i++) {
            Elements headings = doc.select("h" + i);
            for (Element heading : headings) {
                String text = heading.text().trim();
                if (!text.isEmpty()) {
                    sections.add(DocumentSection.builder()
                            .type("heading")
                            .headingLevel(i)
                            .content(text)
                            .pageNumber(1)
                            .build());
                    fullText.append(text).append('\n');
                }
            }
        }

        // 提取段落
        Elements paragraphs = doc.select("p");
        for (Element p : paragraphs) {
            String text = p.text().trim();
            if (!text.isEmpty()) {
                sections.add(DocumentSection.builder()
                        .type("paragraph")
                        .content(text)
                        .pageNumber(1)
                        .build());
                fullText.append(text).append('\n');
            }
        }

        // 提取列表项
        Elements listItems = doc.select("li");
        for (Element li : listItems) {
            String text = li.text().trim();
            if (!text.isEmpty()) {
                sections.add(DocumentSection.builder()
                        .type("list")
                        .content(text)
                        .pageNumber(1)
                        .build());
                fullText.append("- ").append(text).append('\n');
            }
        }

        // 提取表格
        if (options == null || options.isExtractTables()) {
            Elements tableElements = doc.select("table");
            for (Element tableEl : tableElements) {
                List<List<String>> rows = new ArrayList<>();
                Elements trs = tableEl.select("tr");
                for (Element tr : trs) {
                    List<String> row = new ArrayList<>();
                    for (Element cell : tr.select("th,td")) {
                        row.add(cell.text().trim());
                    }
                    if (!row.isEmpty()) {
                        rows.add(row);
                    }
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

        // 提取图片元数据
        if (options == null || options.isExtractImages()) {
            Elements imgElements = doc.select("img");
            for (Element img : imgElements) {
                String src = img.attr("src");
                String alt = img.attr("alt");
                if (!src.isEmpty()) {
                    images.add(DocumentImage.builder()
                            .altText(alt)
                            .build());
                }
            }
        }

        String text = fullText.toString();
        return DocumentContent.builder()
                .text(text)
                .sections(sections)
                .tables(tables)
                .images(images)
                .metadata(DocumentMetadata.builder()
                        .title(doc.title())
                        .charCount(text.length())
                        .build())
                .totalChars(text.length())
                .totalPages(1)
                .build();
    }

    @Override
    public DocumentFormat getSupportedFormat() {
        return DocumentFormat.HTML;
    }
}
