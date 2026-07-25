package com.njydsz.common.excel.tabular;

import java.util.Locale;
import java.util.Optional;

/**
 * 表格数据格式枚举。
 *
 * <p>用于统一标识 Excel (xls/xlsx)、CSV、TSV 等表格数据格式。
 * 通过 {@link #fromExtension(String)} / {@link #fromContentType(String)} 可根据文件扩展名或 MIME 类型自动识别。
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * TabularFormat format = TabularFormat.fromExtension("users.csv").orElseThrow();
 * assert format == TabularFormat.CSV;
 *
 * TabularFormat tsv = TabularFormat.fromContentType("text/tab-separated-values").orElseThrow();
 * assert tsv == TabularFormat.TSV;
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum TabularFormat {

    /** Excel 97-2003 二进制格式 (.xls) */
    EXCEL_XLS("xls", "application/vnd.ms-excel"),
    /** Excel 2007+ OOXML 格式 (.xlsx) */
    EXCEL_XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    /** 逗号分隔值 (.csv) */
    CSV("csv", "text/csv"),
    /** 制表符分隔值 (.tsv) */
    TSV("tsv", "text/tab-separated-values");

    private final String extension;
    private final String contentType;

    TabularFormat(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public String getExtension() {
        return extension;
    }

    public String getContentType() {
        return contentType;
    }

    /**
     * 根据文件扩展名（不含点号）识别格式（不区分大小写）。
     *
     * @param extension 扩展名（如 "csv" / "xlsx" / "XLSX"），允许带前导点号
     * @return 匹配到的格式，未匹配返回 {@link Optional#empty()}
     */
    public static Optional<TabularFormat> fromExtension(String extension) {
        if (extension == null) {
            return Optional.empty();
        }
        String normalized = extension.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        for (TabularFormat f : values()) {
            if (f.extension.equalsIgnoreCase(normalized)) {
                return Optional.of(f);
            }
        }
        return Optional.empty();
    }

    /**
     * 根据 MIME Content-Type 识别格式（精确匹配主类型/子类型，忽略参数）。
     *
     * @param contentType MIME 类型，可为 null
     * @return 匹配到的格式，未匹配返回 {@link Optional#empty()}
     */
    public static Optional<TabularFormat> fromContentType(String contentType) {
        if (contentType == null) {
            return Optional.empty();
        }
        String main = contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
        for (TabularFormat f : values()) {
            if (f.contentType.equalsIgnoreCase(main)) {
                return Optional.of(f);
            }
        }
        return Optional.empty();
    }

    /**
     * 是否为 Excel 格式（xls/xlsx）。
     */
    public boolean isExcel() {
        return this == EXCEL_XLS || this == EXCEL_XLSX;
    }

    /**
     * 是否为文本分隔格式（csv/tsv）。
     */
    public boolean isDelimited() {
        return this == CSV || this == TSV;
    }
}
