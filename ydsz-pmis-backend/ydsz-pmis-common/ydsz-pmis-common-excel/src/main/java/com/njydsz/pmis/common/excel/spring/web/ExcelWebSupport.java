package com.njydsz.pmis.common.excel.spring.web;

import com.njydsz.pmis.common.excel.core.ExcelFacade;
import com.njydsz.pmis.common.excel.core.listener.WriteHandler;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Web download support for ExcelFacade in Spring MVC environment.
 */
public class ExcelFacadeWebSupport {

    private static final Logger log = LoggerFactory.getLogger(ExcelFacadeWebSupport.class);

    /**
     * Write data to HTTP response as Excel file download.
     *
     * @param response HTTP response
     * @param fileName download file name (without extension)
     * @param clazz    data class
     * @param data     data list to write
     * @param <T>      data type
     */
    public static <T> void download(HttpServletResponse response, String fileName,
                                    Class<T> clazz, List<T> data) {
        download(response, fileName, clazz, data, null, null);
    }

    /**
     * Write data to HTTP response as Excel file download with sheet name.
     *
     * @param response  HTTP response
     * @param fileName  download file name (without extension)
     * @param clazz     data class
     * @param data      data list to write
     * @param sheetName sheet name
     * @param <T>       data type
     */
    public static <T> void download(HttpServletResponse response, String fileName,
                                    Class<T> clazz, List<T> data, String sheetName) {
        download(response, fileName, clazz, data, sheetName, null);
    }

    /**
     * Write data to HTTP response as Excel file download with sheet name and write handler.
     *
     * @param response     HTTP response
     * @param fileName     download file name (without extension)
     * @param clazz        data class
     * @param data         data list to write
     * @param sheetName    sheet name
     * @param <T>          data type
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

            ExcelFacade.write(response.getOutputStream(), clazz)
                    .sheet(sheetName != null ? sheetName : "sheet1")
                    .doWrite(data);
        } catch (IOException e) {
            log.error("Failed to write Excel download response", e);
            throw new RuntimeException("Failed to write Excel download response", e);
        }
    }
}
