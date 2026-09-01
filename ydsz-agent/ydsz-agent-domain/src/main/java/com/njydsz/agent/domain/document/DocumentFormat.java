package com.njydsz.agent.domain.document;

/**
 * 文档格式枚举。
 *
 * <p>定义支持的输出文档格式。</p>
 *
 * @author ydsz-agent
 * @since 26.09.01
 */
public enum DocumentFormat {

    /** Markdown 格式 */
    MARKDOWN("md", "text/markdown"),

    /** Word 文档格式 */
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),

    /** PDF 格式 */
    PDF("pdf", "application/pdf"),

    /** HTML 格式 */
    HTML("html", "text/html"),

    /** 纯文本格式 */
    TXT("txt", "text/plain");

    private final String extension;
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
     * 根据扩展名查找格式。
     *
     * @param extension 文件扩展名
     * @return 对应枚举，未找到返回 null
     */
    public static DocumentFormat fromExtension(String extension) {
        if (extension == null) {
            return null;
        }
        String ext = extension.toLowerCase().replace(".", "");
        for (DocumentFormat format : values()) {
            if (format.extension.equals(ext)) {
                return format;
            }
        }
        return null;
    }
}
