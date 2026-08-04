package com.remisoft.common.docs.parser.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.nio.charset.StandardCharsets;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import com.remisoft.common.docs.domain.DocumentContent;
import com.remisoft.common.docs.domain.DocumentImage;
import com.remisoft.common.docs.domain.DocumentMetadata;
import com.remisoft.common.docs.domain.DocumentSection;
import com.remisoft.common.docs.domain.DocumentTable;
import com.remisoft.common.docs.domain.ParseOptions;
import com.remisoft.common.docs.enums.DocumentFormat;
import com.remisoft.common.docs.exception.DocumentException;
import com.remisoft.common.docs.exception.DocumentExceptionCode;
import com.remisoft.common.docs.parser.DocumentParser;

import lombok.extern.slf4j.Slf4j;

/**
 * HTML 文档解析器
 * <p>
 * 解析 HTML 文件，提取标题、段落、表格、图片等结构化内容。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnClass(name = "org.jsoup.Jsoup")
public class HtmlDocumentParser implements DocumentParser {

    /**
     * 清洗 HTML 后按元素类型抽取标题、段落、列表、表格与图片。
     *
     * <p><b>安全前置：</b>先用 {@link Safelist#relaxed()} 过一遍 {@link Jsoup#clean}，
     * 剥离 {@code <script>}、{@code on*} 事件属性等可执行内容后再重新解析。
     * 这是防御 XSS 的关键一步——解析产物可能被回显到前端，
     * 不清洗等于把攻击载荷透传到浏览器。副作用是被 Safelist 拒绝的标签会连同其文本一起消失。
     *
     * <p><b>输出顺序不等于文档顺序：</b>为实现简单，抽取按"全部 h1~h6 → 全部 p →
     * 全部 li"的批次进行，所以 {@code sections} 是按元素类型聚簇排列的，
     * 标题与其所辖正文并不相邻。依赖阅读顺序的下游（如按标题切分章节）不能直接使用该列表。
     *
     * <p>HTML 无分页概念，所有内容的 {@code pageNumber} 统一记为 1。
     * 图片只登记 {@code src} 与 {@code alt} 元信息，<b>不下载二进制</b>，
     * 因此相对路径的 {@code src} 在脱离原站点后不可解析。
     *
     * @param inputStream HTML 字节流，由调用方负责关闭；固定按 UTF-8 解码，
     *                    不识别 {@code <meta charset>} 声明，非 UTF-8 页面会乱码
     * @param fileName    原始文件名，仅用于失败日志定位；元数据标题取自 {@code <title>} 而非此值
     * @param options     解析选项，读取 {@code extractTables} 与 {@code extractImages} 两个开关；
     *                    传 {@code null} 时两者均视为开启
     * @return 文档内容，含分节、表格、图片元信息；页数恒为 1
     * @throws DocumentException 入参流为 {@code null} 时错误码 {@code DOCUMENT_EMPTY}；
     *                           流读取失败时错误码 {@code PARSE_FAILED}。
     *                           HTML 语法错误不会抛异常，Jsoup 会容错解析
     */
    @Override
    public DocumentContent parse(InputStream inputStream, String fileName, ParseOptions options) {
        if (inputStream == null) {
            throw new DocumentException(DocumentExceptionCode.DOCUMENT_EMPTY);
        }

        Document doc;
        try {
            // 使用 Jsoup 从 InputStream 流式解析，避免大文件 OOM
            doc = Jsoup.parse(inputStream, StandardCharsets.UTF_8.name(), "");
            // 使用 Jsoup Safelist 清洗 HTML，移除恶意脚本和事件处理器
            String sanitized = Jsoup.clean(doc.html(), Safelist.relaxed());
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


    /**
     * 声明本解析器在注册中心占据的格式槽位。
     *
     * <p>本类未覆写 {@code supports}，故仅精确匹配 HTML；
     * 结构相近的 {@link DocumentFormat#XML} 需另行处理，不会路由到这里。
     *
     * @return 恒为 {@link DocumentFormat#HTML}
     */
    @Override
    public DocumentFormat getSupportedFormat() {
        return DocumentFormat.HTML;
    }
}
