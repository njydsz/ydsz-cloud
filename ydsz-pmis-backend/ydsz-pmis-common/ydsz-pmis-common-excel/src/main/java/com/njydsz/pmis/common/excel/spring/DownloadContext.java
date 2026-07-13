package com.njydsz.pmis.common.excel.spring;

/**
 * ThreadLocal context for Excel download operations in Spring web environment.
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
