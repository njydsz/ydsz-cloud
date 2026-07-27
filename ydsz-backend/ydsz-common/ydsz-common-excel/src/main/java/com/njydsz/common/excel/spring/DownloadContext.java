package com.njydsz.common.excel.spring;

/**
 * Excel 下载操作 ThreadLocal 上下文
 *
 * <p>在 Spring Web 环境中，用于在当前线程传递 Excel 下载相关上下文信息，
 * 包括文件名和 Sheet 名称。请求结束后必须调用 {@link #clear()} 清理上下文，
 * 防止线程池内存泄漏。</p>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see ExcelWebSupport
 */
public final class DownloadContext {

    private static final ThreadLocal<String> FILE_NAME_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<String> SHEET_NAME_HOLDER = new ThreadLocal<>();

    private DownloadContext() {
    }

    /**
     * Set the download file name for the current thread.
     *
     * @param fileName file name
     */
    public static void setFileName(String fileName) {
        FILE_NAME_HOLDER.set(fileName);
    }

    /**
     * Get the download file name for the current thread.
     *
     * @return file name, or null if not set
     */
    public static String getFileName() {
        return FILE_NAME_HOLDER.get();
    }

    /**
     * Set the sheet name for the current thread.
     *
     * @param sheetName sheet name
     */
    public static void setSheetName(String sheetName) {
        SHEET_NAME_HOLDER.set(sheetName);
    }

    /**
     * Get the sheet name for the current thread.
     *
     * @return sheet name, or null if not set
     */
    public static String getSheetName() {
        return SHEET_NAME_HOLDER.get();
    }

    /**
     * Clear all context for the current thread.
     */
    public static void clear() {
        FILE_NAME_HOLDER.remove();
        SHEET_NAME_HOLDER.remove();
    }
}
