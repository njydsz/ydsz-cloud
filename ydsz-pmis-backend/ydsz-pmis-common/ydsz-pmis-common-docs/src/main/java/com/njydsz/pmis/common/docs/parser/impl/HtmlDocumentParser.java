package com.njydsz.pmis.common.docs.parser.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

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

/**
 * HTML 文档解析器
 * <p>
 * 解析 HTML 文件，提取标题、段落、表格、图片等结构化内容。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
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
            String html = new String(inputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            // 使用 OWASP HTML Sanitizer 清洗恶意脚本（<script>, on* 事件等）
            // 使用 Jsoup Safelist 清洗 HTML，移除恶意脚本和事件处理器
            String sanitized = org.jsoup.Jsoup.clean(html, org.jsoup.safety.Safelist.relaxed());
            doc = Jsoup.parse(sanitized, "");
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
                            .url(src)
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
