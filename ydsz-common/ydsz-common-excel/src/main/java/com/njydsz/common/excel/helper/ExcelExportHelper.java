package com.njydsz.common.excel.helper;

import java.io.ByteArrayOutputStream;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.excel.core.ExcelFacade;
import com.njydsz.common.excel.core.ExcelWriter;
import com.njydsz.common.excel.core.config.ExcelConfig;
import com.njydsz.common.excel.exception.ExcelWriteException;

/**
 * 统一 Excel 导出辅助类 — 封装 common-excel 的导出能力。
 *
 * <p>P1-5: 消除各模块自建导出逻辑的重复编码， 提供统一的导出入口，支持数据导出。
 *
 * <h3>使用示例</h3>
 *
 * <pre>{@code
 * @Resource
 * private ExcelExportHelper exportHelper;
 *
 * // 导出为字节数组
 * byte[] bytes = exportHelper.export("用户列表", UserVO.class, userList);
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class ExcelExportHelper {

  private static final Logger LOG = LoggerFactory.getLogger(ExcelExportHelper.class);

  /** Excel 全局配置。P1-2 修复：Spring 层注入，此前导出恒用 ExcelConfig.defaults() */
  private final ExcelConfig config;

  /** 默认构造（非 Spring 独立使用场景），使用默认配置 */
  public ExcelExportHelper() {
    this(ExcelConfig.defaults());
  }

  /**
   * 构造方法
   *
   * @param config Excel 全局配置，可为 {@code null}（null 时回退默认配置）
   */
  public ExcelExportHelper(ExcelConfig config) {
    this.config = config != null ? config : ExcelConfig.defaults();
  }

  /**
   * 导出数据为 Excel 字节数组。
   * @param sheetName Sheet 名称
   * @param dataClass 数据类型（需有 @ExcelProperty 注解）
   * @param dataList 数据列表
   * @return Excel 文件字节数组
   *
   * @param <T> 泛型类型
   */
  public <T> byte[] export(String sheetName, Class<T> dataClass, List<T> dataList) {
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      ExcelWriter writer = ExcelFacade.write(out, dataClass).config(config).sheet(sheetName);
      writer.doWrite(dataList);
      writer.finish();
      return out.toByteArray();
    } catch (Exception e) {
      LOG.error("[ExcelExportHelper] 导出失败: sheet={}, error={}", sheetName, e.getMessage(), e);
      throw new ExcelWriteException(
          "Excel 导出失败: sheet=" + sheetName + ", error=" + e.getMessage(), e);
    }
  }

  /**
   * 导出数据为 Excel 字节数组（默认 Sheet 名）。
   * @param dataClass 数据类型
   * @param dataList 数据列表
   * @return Excel 文件字节数组
   *
   * @param <T> 泛型类型
   */
  public <T> byte[] export(Class<T> dataClass, List<T> dataList) {
    return export("Sheet1", dataClass, dataList);
  }

  /**
   * 导出动态数据为 Excel 字节数组（自定义表头 + 动态数据行）。
   *
   * <p>适用于无固定 VO 类型的场景（如动态报表）， 统一处理 ByteArrayOutputStream 创建、异常转换与日志记录， 消除各模块自建写入流水线的重复编码。
   *
   * @param sheetName Sheet 名称
   * @param headers 表头列表
   * @param rows 数据行（每行为字段值列表）
   * @return Excel 文件字节数组
   */
  public byte[] export(String sheetName, List<String> headers, List<List<Object>> rows) {
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      // P1-2 修复：接线 config；headRowNumber 语义统一为 1-based（1=表头在第一行），此前传 0 依赖 clamp 兜底
      ExcelWriter writer = ExcelFacade.write(out).config(config).head(headers).headRowNumber(1).sheet(sheetName);
      writer.doWrite(rows);
      writer.finish();
      return out.toByteArray();
    } catch (Exception e) {
      LOG.error("[ExcelExportHelper] 动态导出失败: sheet={}, error={}", sheetName, e.getMessage(), e);
      throw new ExcelWriteException(
          "Excel 动态导出失败: sheet=" + sheetName + ", error=" + e.getMessage(), e);
    }
  }

  /**
   * 导出动态数据为 Excel 字节数组（默认 Sheet 名）。
   *
   * @param headers 表头列表
   * @param rows 数据行
   * @return Excel 文件字节数组
   */
  public byte[] export(List<String> headers, List<List<Object>> rows) {
    return export("Sheet1", headers, rows);
  }
}
