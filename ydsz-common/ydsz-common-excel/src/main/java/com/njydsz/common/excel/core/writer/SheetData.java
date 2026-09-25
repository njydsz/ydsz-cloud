package com.njydsz.common.excel.core.writer;

import java.util.List;

/**
 * 多 Sheet 写入场景下，单个 Sheet 的数据封装。
 *
 * @param <T> 数据类型
 * @author ydsz-team
 * @since 26.10.01
 */
public class SheetData<T> {

  private final String sheetName;
  private final Class<T> clazz;
  private final List<T> data;

  private SheetData(String sheetName, Class<T> clazz, List<T> data) {
    this.sheetName = sheetName;
    this.clazz = clazz;
    this.data = data;
  }

  /**
   * 创建 SheetData 实例。
   *
   * @param sheetName Sheet 名称（1-31 字符，不可含 / \ ? * [ ]）
   * @param clazz 数据类型
   * @param data 数据列表
   * @param <T> 数据类型
   * @return SheetData 实例
   */
  public static <T> SheetData<T> of(String sheetName, Class<T> clazz, List<T> data) {
    return new SheetData<>(sheetName, clazz, data);
  }

  public String getSheetName() {
    return sheetName;
  }

  public Class<T> getClazz() {
    return clazz;
  }

  public List<T> getData() {
    return data;
  }
}
