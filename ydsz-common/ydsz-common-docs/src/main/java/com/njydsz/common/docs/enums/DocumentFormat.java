package com.njydsz.common.docs.enums;

import java.io.InputStream;

import org.apache.tika.Tika;

/**
 * 文档格式枚举
 * <p>
 * 定义系统支持的所有文档格式类型，用于解析器路由和格式检测。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum DocumentFormat {

    /** PDF 文档 */
    PDF("pdf", "application/pdf"),
    /** Word 文档（.docx） */
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    /** Word 文档（.doc，旧格式） */
    DOC("doc", "application/msword"),
    /** Excel 文档（.xlsx） */
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    /** Excel 文档（.xls，旧格式） */
    XLS("xls", "application/vnd.ms-excel"),
    /** PowerPoint 文档（.pptx） */
    PPTX("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
    /** PowerPoint 文档（.ppt，旧格式） */
    PPT("ppt", "application/vnd.ms-powerpoint"),
    /** Word 文档（.docm，含宏） */
    DOCM("docm", "application/vnd.ms-word.document.macroEnabled.12"),
    /** Excel 文档（.xlsm，含宏） */
    XLSM("xlsm", "application/vnd.ms-excel.sheet.macroEnabled.12"),
    /** PowerPoint 文档（.pptm，含宏） */
    PPTM("pptm", "application/vnd.ms-powerpoint.presentation.macroEnabled.12"),
    /** HTML 文档 */
    HTML("html", "text/html"),
    /** Markdown 文档 */
    MARKDOWN("md", "text/markdown"),
    /** 纯文本文档 */
    TXT("txt", "text/plain"),
    /** CSV 文档 */
    CSV("csv", "text/csv"),
    /** XML 文档 */
    XML("xml", "application/xml"),
    /** RTF 文档 */
    RTF("rtf", "application/rtf"),
    /** 未知格式 */
    UNKNOWN("unknown", "application/octet-stream");

    /** 文件扩展名 */
    private final String extension;
    /** MIME 类型 */
    private final String mimeType;

    /** Tika 实例复用（加载开销较大） */
    private static final Tika TIKA = new Tika();

    DocumentFormat(String extension, String mimeType) {
        this.extension = extension;
        this.mimeType = mimeType;
    }

    /**
     * 获取该格式的标准文件扩展名（不含点号）。
     *
     * <p>该值是 {@link #fromFileName(String)} 做扩展名匹配的依据，
     * 同时也用于生成下载文件名。注意一种格式只登记一个扩展名，
     * 例如 {@link #MARKDOWN} 只登记 {@code md}，
     * 因此 {@code .markdown} 后缀的文件会被推断为 {@link #UNKNOWN}。
     *
     * @return 小写扩展名，如 {@code pdf}、{@code docx}；{@link #UNKNOWN} 返回 {@code unknown}
     */
    public String getExtension() {
        return extension;
    }

    /**
     * 获取该格式对应的标准 MIME 类型。
     *
     * <p>该值同时用于两个场景：一是 {@link #fromContent(InputStream)} 中与 Tika
     * 嗅探结果做等值比对，二是文档下载时回填 HTTP {@code Content-Type} 响应头。
     *
     * @return MIME 类型字符串；{@link #UNKNOWN} 返回通用二进制类型 {@code application/octet-stream}
     */
    public String getMimeType() {
        return mimeType;
    }

    /**
     * 使用 Apache Tika 检测文档的实际 MIME 类型
     * <p>
     * 与文件扩展名交叉验证，不匹配时返回 UNKNOWN。
     *
     * @param inputStream 文档输入流（会消耗部分字节）
     * @return 检测到的文档格式，无法确定时返回 {@link #UNKNOWN}
     */
    public static DocumentFormat fromContent(InputStream inputStream) {
        try {
            String mimeType = TIKA.detect(inputStream);
            for (DocumentFormat format : values()) {
                if (format.mimeType.equals(mimeType)) {
                    return format;
                }
            }
        } catch (Exception ignored) {
            // Tika 检测失败时回退到扩展名
        }
        return UNKNOWN;
    }

    /**
     * 根据文件扩展名推断文档格式
     *
     * @param fileName 文件名
     * @return 对应的文档格式，未匹配时返回 {@link #UNKNOWN}
     */
    public static DocumentFormat fromFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return UNKNOWN;
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return UNKNOWN;
        }
        String ext = fileName.substring(dotIndex + 1).toLowerCase();
        for (DocumentFormat format : values()) {
            if (format.extension.equals(ext)) {
                return format;
            }
        }
        return UNKNOWN;
    }
}
