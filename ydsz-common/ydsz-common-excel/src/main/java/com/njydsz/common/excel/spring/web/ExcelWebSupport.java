package com.njydsz.common.excel.spring.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.excel.core.ExcelFacade;
import com.njydsz.common.excel.core.listener.WriteHandler;
import com.njydsz.common.excel.exception.ExcelExceptionCode;
import com.njydsz.common.excel.exception.ExcelWriteException;
import com.njydsz.common.excel.spring.DownloadContext;

/**
 * Excel Web 下载支持 — Spring MVC 环境下的 HTTP 响应写入
 *
 * <p>自动设置 Content-Type、Content-Disposition 等 HTTP 头，
 * 并确保 ThreadLocal 上下文在请求结束后被清理，防止线程池内存泄漏。</p>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class ExcelWebSupport {

    private static final Logger log = LoggerFactory.getLogger(ExcelWebSupport.class);

    /**
     * 写入 Excel 文件到 HTTP 响应（默认 Sheet 名）
     *
     * @param response HTTP 响应
     * @param fileName 下载文件名（不含扩展名）
     * @param clazz    数据类型
     * @param data     数据列表
     * @param <T>      数据泛型
     */
    public static <T> void download(HttpServletResponse response, String fileName,
                                    Class<T> clazz, List<T> data) {
        download(response, fileName, clazz, data, null, null);
    }

    /**
     * 写入 Excel 文件到 HTTP 响应（指定 Sheet 名）
     *
     * @param response  HTTP 响应
     * @param fileName  下载文件名（不含扩展名）
     * @param clazz     数据类型
     * @param data      数据列表
     * @param sheetName Sheet 名称
     * @param <T>       数据泛型
     */
    public static <T> void download(HttpServletResponse response, String fileName,
                                    Class<T> clazz, List<T> data, String sheetName) {
        download(response, fileName, clazz, data, sheetName, null);
    }

    /**
     * 写入 Excel 文件到 HTTP 响应（指定 Sheet 名和写入处理器）
     *
     * <p>使用 try-finally 确保 ThreadLocal 上下文在请求结束后被清理，
     * 防止 Servlet 线程池中的线程复用导致内存泄漏。</p>
     *
     * @param response     HTTP 响应
     * @param fileName     下载文件名（不含扩展名）
     * @param clazz        数据类型
     * @param data         数据列表
     * @param sheetName    Sheet 名称
     * @param writeHandler 写入处理器
     * @param <T>          数据泛型
     */
    public static <T> void download(HttpServletResponse response, String fileName,
                                    Class<T> clazz, List<T> data, String sheetName,
                                    WriteHandler writeHandler) {
        try {
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20");
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition",
                    "attachment; filename*=UTF-8''" + encodedFileName + ".xlsx");

            // 设置下载上下文
            DownloadContext.setFileName(fileName + ".xlsx");
            if (sheetName != null) {
                DownloadContext.setSheetName(sheetName);
            }

            // 先写入 ByteArrayOutputStream 获取 Content-Length，再写入 response
            // 对于大文件场景（>10MB），建议使用 ExcelFacade.write(OutputStream) 直接流式写入
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ExcelFacade.write(baos, clazz)
                    .sheet(sheetName != null ? sheetName : "Sheet1")
                    .doWrite(data);
            byte[] bytes = baos.toByteArray();
            response.setContentLength(bytes.length);
            response.getOutputStream().write(bytes);
            response.getOutputStream().flush();
        } catch (IOException e) {
            log.error("Excel 下载写入失败: fileName={}", fileName, e);
            throw new ExcelWriteException(ExcelExceptionCode.WRITE_IO_ERROR,
                "Excel 下载写入失败: " + fileName, e);
        } finally {
            // 确保 ThreadLocal 被清理，防止线程池内存泄漏
            DownloadContext.clear();
        }
    }
}
