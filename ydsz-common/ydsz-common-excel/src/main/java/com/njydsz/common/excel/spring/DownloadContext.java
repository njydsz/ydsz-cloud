package com.njydsz.common.excel.spring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Excel 下载操作 ThreadLocal 上下文
 *
 * <p>在 Spring Web 环境中，用于在当前线程传递 Excel 下载相关上下文信息，
 * 包括文件名和 Sheet 名称。请求结束后必须清理上下文防止线程池内存泄漏。</p>
 *
 * <p>推荐使用 {@link #withContext(String, String, Runnable)} 自动清理模式，
 * 确保即使发生异常也能正确清除 ThreadLocal 值。</p>
 *
 * <h3>推荐用法</h3>
 * <pre>{@code
 * DownloadContext.withContext(reportName, "数据", () -> {
 *     ExcelFacade.write(response.getOutputStream(), Data.class)
 *         .sheet("数据").doWrite(dataList);
 * });
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see ExcelWebSupport
 */
public final class DownloadContext {

    private static final Logger LOGGER = LoggerFactory.getLogger(DownloadContext.class);

    private static final ThreadLocal<String> FILE_NAME_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<String> SHEET_NAME_HOLDER = new ThreadLocal<>();

    private DownloadContext() {
    }

    /**
     * 在当前线程设置下载文件名。
     *
     * @param fileName 文件名，可为 {@code null} 以清除
     */
    public static void setFileName(String fileName) {
        FILE_NAME_HOLDER.set(fileName);
    }

    /**
     * 获取当前线程的下载文件名。
     *
     * @return 文件名，未设置时返回 {@code null}
     */
    public static String getFileName() {
        return FILE_NAME_HOLDER.get();
    }

    /**
     * 在当前线程设置 Sheet 名称。
     *
     * @param sheetName Sheet 名称，可为 {@code null} 以清除
     */
    public static void setSheetName(String sheetName) {
        SHEET_NAME_HOLDER.set(sheetName);
    }

    /**
     * 获取当前线程的 Sheet 名称。
     *
     * @return Sheet 名称，未设置时返回 {@code null}
     */
    public static String getSheetName() {
        return SHEET_NAME_HOLDER.get();
    }

    /**
     * 使用 try-with-resources 风格自动清理上下文。
     *
     * <p>无论 {@code action} 是否抛出异常，都会在结束后自动调用 {@link #clear()}，
     * 避免线程池场景下 ThreadLocal 内存泄漏。</p>
     *
     * @param fileName 下载文件名
     * @param sheetName Sheet 名称
     * @param action 要在上下文中执行的操作
     */
    public static void withContext(String fileName, String sheetName, Runnable action) {
        setFileName(fileName);
        setSheetName(sheetName);
        try {
            action.run();
        } finally {
            clear();
        }
    }

    /**
     * 清理当前线程的全部上下文字段。
     *
     * <p>应在请求结束时调用（建议在 {@code Filter} 或 {@code Interceptor} 的
     * {@code afterCompletion} 中保证执行）。手动调用 {@link #withContext} 模式时
     * 无需再调用此方法。</p>
     */
    public static void clear() {
        FILE_NAME_HOLDER.remove();
        SHEET_NAME_HOLDER.remove();
        LOGGER.debug("DownloadContext cleared for current thread");
    }
}
