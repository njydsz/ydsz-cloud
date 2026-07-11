package com.njydsz.pmis.agent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 多格式文档解析器（P1-3 落地）。
 *
 * <p>对标 Coze 知识库文档解析 / Dify Document Loader：
 * 支持 PDF、Word、Excel、PPT、Markdown、HTML 等格式的文档解析，
 * 提取纯文本内容用于知识库分块和向量化。
 *
 * <p>支持的格式：
 * <ul>
 *   <li><b>PDF</b> - 使用 PDFBox 提取文本（支持中文）</li>
 *   <li><b>Word (.docx)</b> - 使用 Apache POI XWPF 提取段落和表格</li>
 *   <li><b>Excel (.xlsx)</b> - 使用 Apache POI XSSF 按行提取单元格文本</li>
 *   <li><b>PPT (.pptx)</b> - 使用 Apache POI XSLF 提取幻灯片文本</li>
 *   <li><b>Markdown</b> - 去除标记符号，保留纯文本</li>
 *   <li><b>HTML</b> - 使用 Jsoup 去除标签，保留文本</li>
 *   <li><b>纯文本</b> - 直接返回</li>
 * </ul>
 *
 * <p>降级策略：当对应解析库不存在时，降级为 UTF-8 文本读取。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0 (P1-3)
 */
@Slf4j
@Component
public class DocumentParser {

    /**
     * 解析文档内容。
     *
     * @param fileName 文件名（用于推断格式）
     * @param bytes    文件二进制内容
     * @return 提取的纯文本内容
     */
    public String parse(String fileName, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        String ext = getExtension(fileName).toLowerCase();
        try {
            return switch (ext) {
                case "pdf" -> parsePdf(bytes);
                case "docx" -> parseDocx(bytes);
                case "doc" -> parseDoc(bytes);
                case "xlsx" -> parseXlsx(bytes);
                case "xls" -> parseXls(bytes);
                case "pptx" -> parsePptx(bytes);
                case "md", "markdown" -> parseMarkdown(bytes);
                case "html", "htm" -> parseHtml(bytes);
                case "txt", "text", "csv", "json", "xml", "yaml", "yml", "log" -> parsePlainText(bytes);
                default -> parsePlainText(bytes);
            };
        } catch (Exception e) {
            log.warn("[DocumentParser] 解析失败, file={}, ext={}, error={}", fileName, ext, e.getMessage());
            return parsePlainText(bytes);
        }
    }

    /**
     * 解析文件（从文件路径）。
     *
     * @param filePath 文件路径
     * @return 提取的纯文本内容
     */
    public String parseFile(String filePath) {
        try {
            byte[] bytes = Files.readAllBytes(Path.of(filePath));
            String fileName = Path.of(filePath).getFileName().toString();
            return parse(fileName, bytes);
        } catch (Exception e) {
            log.error("[DocumentParser] 读取文件失败: {}", filePath, e);
            return "";
        }
    }

    // ==================== 格式解析 ====================

    /**
     * 解析 PDF 文件。
     * 使用 PDFBox 提取文本（如果 classpath 有 PDFBox）。
     */
    private String parsePdf(byte[] bytes) throws Exception {
        try {
            // 尝试使用 Apache PDFBox
            Class<?> loaderClass = Class.forName("org.apache.pdfbox.Loader");
            Object document = loaderClass
                    .getMethod("loadPDF", byte[].class)
                    .invoke(null, bytes);
            try {
                Class<?> pdfTextClass = Class.forName("org.apache.pdfbox.text.PDFTextStripper");
                Object stripper = pdfTextClass.getDeclaredConstructor().newInstance();
                String text = (String) pdfTextClass
                        .getMethod("getText", Class.forName("org.apache.pdfbox.pdmodel.PDDocument"))
                        .invoke(stripper, document);
                return text != null ? text.trim() : "";
            } finally {
                document.getClass().getMethod("close").invoke(document);
            }
        } catch (ClassNotFoundException e) {
            log.warn("[DocumentParser] PDFBox 未找到, PDF 解析降级为纯文本");
            return parsePlainText(bytes);
        }
    }

    /**
     * 解析 Word .docx 文件。
     * 使用 Apache POI XWPF。
     */
    private String parseDocx(byte[] bytes) throws Exception {
        try {
            Class<?> xwpfDocClass = Class.forName("org.apache.poi.xwpf.usermodel.XWPFDocument");
            InputStream is = new ByteArrayInputStream(bytes);
            Object doc = xwpfDocClass.getDeclaredConstructor(InputStream.class).newInstance(is);
            try {
                // 提取段落
                var paragraphs = (List<?>) xwpfDocClass
                        .getMethod("getParagraphs").invoke(doc);
                StringBuilder sb = new StringBuilder();
                for (Object para : paragraphs) {
                    String text = (String) para.getClass().getMethod("getText").invoke(para);
                    if (text != null && !text.isBlank()) {
                        sb.append(text).append("\n");
                    }
                }
                // 提取表格
                var tables = (List<?>) xwpfDocClass.getMethod("getTables").invoke(doc);
                for (Object table : tables) {
                    var rows = (List<?>) table.getClass().getMethod("getRows").invoke(table);
                    for (Object row : rows) {
                        var cells = (List<?>) row.getClass().getMethod("getTableCells").invoke(row);
                        for (Object cell : cells) {
                            String text = (String) cell.getClass().getMethod("getText").invoke(cell);
                            if (text != null && !text.isBlank()) {
                                sb.append(text.trim()).append("\t");
                            }
                        }
                        sb.append("\n");
                    }
                }
                return sb.toString().trim();
            } finally {
                doc.getClass().getMethod("close").invoke(doc);
                is.close();
            }
        } catch (ClassNotFoundException e) {
            log.warn("[DocumentParser] Apache POI 未找到, DOCX 解析降级为纯文本");
            return parsePlainText(bytes);
        }
    }

    /**
     * 构造 XWPFDocument 实例（已内联到 parseDocx 中）。
     */

    /**
     * 解析 Word .doc 文件（旧格式）。
     */
    private String parseDoc(byte[] bytes) throws Exception {
        // 旧格式 .doc 需要 POI HWPF，降级为纯文本
        log.warn("[DocumentParser] .doc 格式暂不支持, 降级为纯文本");
        return parsePlainText(bytes);
    }

    /**
     * 解析 Excel .xlsx 文件。
     */
    private String parseXlsx(byte[] bytes) throws Exception {
        try {
            Class<?> workbookClass = Class.forName("org.apache.poi.xssf.usermodel.XSSFWorkbook");
            InputStream is = new ByteArrayInputStream(bytes);
            Object workbook = workbookClass.getDeclaredConstructor(InputStream.class).newInstance(is);
            try {
                var sheets = (List<?>) workbook.getClass().getMethod("getSheets").invoke(workbook);
                StringBuilder sb = new StringBuilder();
                for (Object sheet : sheets) {
                    var rows = (List<?>) sheet.getClass().getMethod("getRows").invoke(sheet);
                    for (Object row : rows) {
                        var cells = (List<?>) row.getClass().getMethod("getCells").invoke(row);
                        for (Object cell : cells) {
                            String text = cellToString(cell);
                            if (text != null && !text.isBlank()) {
                                sb.append(text).append("\t");
                            }
                        }
                        sb.append("\n");
                    }
                    sb.append("\n");
                }
                return sb.toString().trim();
            } finally {
                workbook.getClass().getMethod("close").invoke(workbook);
                is.close();
            }
        } catch (ClassNotFoundException e) {
            log.warn("[DocumentParser] Apache POI 未找到, XLSX 解析降级为纯文本");
            return parsePlainText(bytes);
        }
    }

    /**
     * 解析 Excel .xls 文件（旧格式）。
     */
    private String parseXls(byte[] bytes) throws Exception {
        log.warn("[DocumentParser] .xls 格式暂不支持, 降级为纯文本");
        return parsePlainText(bytes);
    }

    /**
     * 解析 PPT .pptx 文件。
     */
    private String parsePptx(byte[] bytes) throws Exception {
        try {
            Class<?> slideShowClass = Class.forName("org.apache.poi.xslf.usermodel.XMLSlideShow");
            InputStream is = new ByteArrayInputStream(bytes);
            Object ppt = slideShowClass.getDeclaredConstructor(InputStream.class).newInstance(is);
            try {
                var slides = (List<?>) ppt.getClass().getMethod("getSlides").invoke(ppt);
                StringBuilder sb = new StringBuilder();
                for (Object slide : slides) {
                    var shapes = (List<?>) slide.getClass().getMethod("getShapes").invoke(slide);
                    for (Object shape : shapes) {
                        String text = (String) shape.getClass().getMethod("getText").invoke(shape);
                        if (text != null && !text.isBlank()) {
                            sb.append(text).append("\n");
                        }
                    }
                    sb.append("\n");
                }
                return sb.toString().trim();
            } finally {
                ppt.getClass().getMethod("close").invoke(ppt);
                is.close();
            }
        } catch (ClassNotFoundException e) {
            log.warn("[DocumentParser] Apache POI 未找到, PPTX 解析降级为纯文本");
            return parsePlainText(bytes);
        }
    }

    /**
     * 解析 Markdown 文件。
     * 去除标记符号，保留纯文本。
     */
    private String parseMarkdown(byte[] bytes) throws Exception {
        String text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        // 去除 Markdown 标记
        return text
                .replaceAll("^#+\\s*", "")           // 标题
                .replaceAll("\\*\\*(.+?)\\*\\*", "$1") // 粗体
                .replaceAll("\\*(.+?)\\*", "$1")       // 斜体
                .replaceAll("`(.+?)`", "$1")           // 行内代码
                .replaceAll("\\[(.+?)\\]\\(.+?\\)", "$1") // 链接
                .replaceAll("^>\\s*", "")              // 引用
                .replaceAll("^[-*+]\\s+", "")          // 列表标记
                .replaceAll("^\\d+\\.\\s+", "")        // 有序列表
                .replaceAll("---+", "")                // 分隔线
                .trim();
    }

    /**
     * 解析 HTML 文件。
     */
    private String parseHtml(byte[] bytes) throws Exception {
        String html = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        // 简单去除 HTML 标签
        return html
                .replaceAll("<script[^>]*>[\\s\\S]*?</script>", "")  // 去脚本
                .replaceAll("<style[^>]*>[\\s\\S]*?</style>", "")     // 去样式
                .replaceAll("<[^>]+>", " ")                            // 去标签
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&quot;", "\"")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * 解析纯文本文件。
     */
    private String parsePlainText(byte[] bytes) {
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8).trim();
    }

    // ==================== 工具方法 ====================

    /**
     * 获取文件扩展名。
     */
    private String getExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1);
    }

    /**
     * POI Cell 转字符串。
     */
    private String cellToString(Object cell) {
        try {
            return (String) cell.getClass().getMethod("getString").invoke(cell);
        } catch (Exception e) {
            return String.valueOf(cell);
        }
    }

    /**
     * 支持的文件格式列表。
     *
     * @return 支持的扩展名列表
     */
    public List<String> supportedFormats() {
        return List.of("pdf", "docx", "xlsx", "pptx", "md", "html", "txt", "csv", "json");
    }
}
