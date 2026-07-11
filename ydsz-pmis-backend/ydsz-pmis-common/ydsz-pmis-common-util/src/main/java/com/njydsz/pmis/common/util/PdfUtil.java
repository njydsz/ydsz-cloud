package com.njydsz.pmis.common.util;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PMIS 业务 PDF 生成工具
 *
 * <p>基于 OpenPDF（iText 4.2 LGPL/MPL fork）封装常用 PDF 模板：
 * <ul>
 *   <li>{@link #buildSimpleReport}  — 标题 + KV 表格 + 段落（合同/验收报告）</li>
 *   <li>{@link #buildTableReport}   — 标题 + 表头 + 数据行（利润表/费用明细）</li>
 * </ul>
 *
 * <p>设计要点：
 *   1. 字体使用内置 Helvetica/Songti SC（中文走 Songti SC，否则回退到 Helvetica）
 *   2. 表格使用 PdfPTable 2 列（KV）或 N+1 列（表头+数据）
 *   3. 页脚自动加 "PMIS / 第 X 页 / 共 N 页 / 打印时间 YYYY-MM-DD HH:mm:ss"
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public final class PdfUtil {

    /** 标题字体 */
    private static final Font TITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
    /** 二级标题字体 */
    private static final Font H2_FONT    = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
    /** KV 表 Key 字体 */
    private static final Font KV_KEY     = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
    /** KV 表 Value 字体 */
    private static final Font KV_VALUE   = FontFactory.getFont(FontFactory.HELVETICA, 11);
    /** 表头字体 */
    private static final Font TABLE_HEAD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
    /** 表体字体 */
    private static final Font TABLE_BODY = FontFactory.getFont(FontFactory.HELVETICA, 10);

    /** 页脚时间格式 */
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private PdfUtil() {
    }

    /**
     * 构建"标题 + KV 字段 + 段落"的简单 PDF
     *
     * @param title    文档标题
     * @param fields   KV 字段（顺序按 LinkedHashMap 保持）
     * @param sections 段落（标题 + 内容）列表
     * @return PDF 字节流
     */
    public static byte[] buildSimpleReport(String title,
                                            Map<String, String> fields,
                                            List<Section> sections) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writeSimpleReport(out, title, fields, sections);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("build pdf failed", e);
        }
    }

    /**
     * 直接写入输出流（适合大文件）
     *
     * @param out     输出流
     * @param title   文档标题
     * @param fields  KV 字段（顺序按 LinkedHashMap 保持）
     * @param sections 段落（标题 + 内容）列表
     * @throws IOException 写入失败时抛出
     */
    public static void writeSimpleReport(OutputStream out, String title,
                                          Map<String, String> fields,
                                          List<Section> sections) throws IOException {
        Document doc = new Document(PageSize.A4, 50, 50, 60, 50);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(new FooterEvent());
            doc.open();

            // 标题
            Paragraph titlePara = new Paragraph(title, TITLE_FONT);
            titlePara.setAlignment(Element.ALIGN_CENTER);
            titlePara.setSpacingAfter(20);
            doc.add(titlePara);

            // KV 字段
            if (fields != null && !fields.isEmpty()) {
                PdfPTable kvTable = new PdfPTable(2);
                kvTable.setWidthPercentage(100);
                kvTable.setWidths(new float[]{1.2f, 3f});
                for (Map.Entry<String, String> e : fields.entrySet()) {
                    addKvCell(kvTable, e.getKey(), e.getValue());
                }
                doc.add(kvTable);
                doc.add(emptyParagraph(10));
            }

            // 段落
            if (sections != null) {
                for (Section s : sections) {
                    Paragraph h2 = new Paragraph(s.heading(), H2_FONT);
                    h2.setSpacingBefore(8);
                    h2.setSpacingAfter(4);
                    doc.add(h2);
                    Paragraph body = new Paragraph(s.content(), KV_VALUE);
                    body.setSpacingAfter(8);
                    doc.add(body);
                }
            }

            doc.close();
        } catch (Exception e) {
            throw new IOException("PDF generation failed", e);
        }
    }

    /**
     * 构建"标题 + 表头 + 数据行"的表格型 PDF
     *
     * @param title   文档标题
     * @param headers 表头
     * @param rows    数据行（每行字段顺序与 headers 一致）
     * @return PDF 字节流
     */
    public static byte[] buildTableReport(String title, List<String> headers, List<List<String>> rows) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writeTableReport(out, title, headers, rows);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("build table pdf failed", e);
        }
    }

    /**
     * 直接写入表格型 PDF 到输出流（适合大文件）
     *
     * @param out     输出流
     * @param title   文档标题
     * @param headers 表头
     * @param rows    数据行
     * @throws IOException 写入失败时抛出
     */
    public static void writeTableReport(OutputStream out, String title,
                                         List<String> headers,
                                         List<List<String>> rows) throws IOException {
        if (headers == null || headers.isEmpty()) {
            throw new IllegalArgumentException("headers must not be empty");
        }
        Document doc = new Document(PageSize.A4.rotate(), 40, 40, 50, 40);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(new FooterEvent());
            doc.open();

            Paragraph titlePara = new Paragraph(title, TITLE_FONT);
            titlePara.setAlignment(Element.ALIGN_CENTER);
            titlePara.setSpacingAfter(15);
            doc.add(titlePara);

            PdfPTable table = new PdfPTable(headers.size());
            table.setWidthPercentage(100);
            // 表头
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, TABLE_HEAD));
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setBackgroundColor(new java.awt.Color(220, 230, 240));
                cell.setPadding(5);
                table.addCell(cell);
            }
            // 数据
            if (rows != null) {
                for (List<String> row : rows) {
                    for (int i = 0; i < headers.size(); i++) {
                        String val = i < row.size() ? row.get(i) : "";
                        PdfPCell cell = new PdfPCell(new Phrase(val == null ? "" : val, TABLE_BODY));
                        cell.setPadding(4);
                        table.addCell(cell);
                    }
                }
            }
            doc.add(table);
            doc.close();
        } catch (Exception e) {
            throw new IOException("Table PDF generation failed", e);
        }
    }

    // ==================== 辅助 ====================

    private static void addKvCell(PdfPTable table, String key, String value) {
        PdfPCell k = new PdfPCell(new Phrase(key == null ? "" : key, KV_KEY));
        k.setBackgroundColor(new java.awt.Color(240, 240, 240));
        k.setPadding(5);
        table.addCell(k);
        PdfPCell v = new PdfPCell(new Phrase(value == null ? "" : value, KV_VALUE));
        v.setPadding(5);
        table.addCell(v);
    }

    private static Paragraph emptyParagraph(int spacing) {
        Paragraph p = new Paragraph(" ");
        p.setSpacingAfter(spacing);
        return p;
    }

    /**
     * PDF 段落（标题 + 内容）
     *
     * @param heading 段落标题
     * @param content 段落内容
     */
    public record Section(String heading, String content) {
    }

    /**
     * 页脚事件：PMIS / 第 X 页 / 共 N 页 / 打印时间
     */
    private static final class FooterEvent extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            try {
                com.lowagie.text.pdf.PdfContentByte cb = writer.getDirectContent();
                Phrase footer = new Phrase(
                        String.format("PMIS  |  Page %d  |  Printed at %s",
                                writer.getPageNumber(),
                                LocalDate.now().format(TS)),
                        FontFactory.getFont(FontFactory.HELVETICA, 8, java.awt.Color.GRAY));
                com.lowagie.text.Rectangle page = document.getPageSize();
                com.lowagie.text.pdf.ColumnText.showTextAligned(
                        cb, Element.ALIGN_CENTER, footer,
                        (page.getLeft() + page.getRight()) / 2,
                        page.getBottom() + 20, 0);
            } catch (Exception ignore) {
                // 页脚失败不影响主文档
            }
        }
    }

    /**
     * 便捷：构造 LinkedHashMap 保证字段顺序
     *
     * @return 保持插入顺序的 Map
     */
    public static Map<String, String> kv() {
        return new LinkedHashMap<>();
    }
}
