package com.njydsz.pmis.common.excel.spring;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import com.njydsz.pmis.common.excel.core.ExcelFacade;
import com.njydsz.pmis.common.excel.core.config.ExcelConfig;
import com.njydsz.pmis.common.excel.core.listener.ReadListener;

/**
 * Spring template for ExcelFacade operations.
 * Provides a convenient API for common Excel operations in Spring applications.
 */
public class ExcelTemplate {

    private final ExcelConfig config;

    public ExcelTemplate() {
        this(ExcelConfig.getInstance());
    }

    public ExcelTemplate(ExcelConfig config) {
        this.config = config;
    }

    /**
     * Read Excel from input stream.
     *
     * @param inputStream input stream
     * @param clazz       target class
     * @param listener    read listener
     * @param <T>         data type
     */
    public <T> void read(InputStream inputStream, Class<T> clazz, ReadListener<T> listener) {
        ExcelFacade.read(inputStream, clazz, listener);
    }

    /**
     * Write data to output stream.
     *
     * @param outputStream output stream
     * @param clazz        data class
     * @param data         data list
     * @param <T>          data type
     */
    public <T> void write(OutputStream outputStream, Class<T> clazz, List<T> data) {
        ExcelFacade.write(outputStream, clazz).sheet("sheet1").doWrite(data);
    }

    /**
     * Write data to output stream with sheet name.
     *
     * @param outputStream output stream
     * @param clazz        data class
     * @param data         data list
     * @param sheetName    sheet name
     * @param <T>          data type
     */
    public <T> void write(OutputStream outputStream, Class<T> clazz, List<T> data, String sheetName) {
        ExcelFacade.write(outputStream, clazz).sheet(sheetName).doWrite(data);
    }

    /**
     * Get the configuration.
     *
     * @return config
     */
    public ExcelConfig getConfig() {
        return config;
    }
}
