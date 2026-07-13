package com.njydsz.pmis.common.docs.enums;

/**
 * 文档格式枚举
 * <p>
 * 定义系统支持的所有文档格式类型，用于解析器路由和格式检测。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 1.0.0
 * @since 1.3.0
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

    DocumentFormat(String extension, String mimeType) {
        this.extension = extension;
        this.mimeType = mimeType;
    }

    public String getExtension() {
        return extension;
    }

    public String getMimeType() {
        return mimeType;
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
