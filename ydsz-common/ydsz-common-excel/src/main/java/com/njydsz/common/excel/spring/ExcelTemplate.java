package com.njydsz.common.excel.spring;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import com.njydsz.common.excel.core.ExcelFacade;
import com.njydsz.common.excel.core.config.ExcelConfig;
import com.njydsz.common.excel.core.listener.ReadListener;

/**
 * Excel 操作 Spring 模板类
 *
 * <p>为 Spring 应用提供便捷的 Excel 读写 API 封装。 内部委托 {@link ExcelFacade} 实现核心功能，简化调用方式。
 *
 * <h3>使用示例</h3>
 *
 * <pre>{@code
 * // 注入模板
 * @Autowired
 * private ExcelTemplate excelTemplate;
 *
 * // 读取 Excel
 * excelTemplate.read(inputStream, User.class, listener);
 *
 * // 写入 Excel
 * excelTemplate.write(outputStream, User.class, userList);
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see ExcelFacade
 */
public class ExcelTemplate {

  private final ExcelConfig config;

  public ExcelTemplate() {
    this(ExcelConfig.defaults());
  }

  public ExcelTemplate(ExcelConfig config) {
    this.config = config;
  }

  /**
   * Read Excel from input stream.
   *
   * @param inputStream input stream
   * @param clazz target class
   * @param listener read listener
   * @param <T> data type
   */
  public <T> void read(InputStream inputStream, Class<T> clazz, ReadListener<T> listener) {
    // P1-2 修复：接线注入的 ExcelConfig。此前直接委托静态门面，读取恒用 ExcelConfig.defaults()，
    // ydsz.excel.* 配置（maxReadFileSizeMb / useFastReader / validationMode 等）完全不生效。
    ExcelFacade.read(inputStream, clazz).config(config).sheet().doRead(listener);
  }

  /**
   * Write data to output stream.
   *
   * @param outputStream output stream
   * @param clazz data class
   * @param data data list
   * @param <T> data type
   */
  public <T> void write(OutputStream outputStream, Class<T> clazz, List<T> data) {
    // P1-2 修复：接线注入的 ExcelConfig（此前恒用默认配置）
    ExcelFacade.write(outputStream, clazz).config(config).sheet("sheet1").doWrite(data);
  }

  /**
   * Write data to output stream with sheet name.
   *
   * @param outputStream output stream
   * @param clazz data class
   * @param data data list
   * @param sheetName sheet name
   * @param <T> data type
   */
  public <T> void write(OutputStream outputStream, Class<T> clazz, List<T> data, String sheetName) {
    // P1-2 修复：接线注入的 ExcelConfig（此前恒用默认配置）
    ExcelFacade.write(outputStream, clazz).config(config).sheet(sheetName).doWrite(data);
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
