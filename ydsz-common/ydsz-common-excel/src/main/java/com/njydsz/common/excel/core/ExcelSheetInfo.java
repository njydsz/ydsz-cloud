package com.njydsz.common.excel.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import com.njydsz.common.excel.exception.ExcelReadException;

/**
 * Excel Sheet信息查询工具
 *
 * <p>提供查询Excel文件中Sheet数量、名称等信息的便捷方法。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class ExcelSheetInfo {

  private final String name;
  private final int index;
  private final int rowCount;
  private final int columnCount;

  public ExcelSheetInfo(String name, int index, int rowCount, int columnCount) {
    this.name = name;
    this.index = index;
    this.rowCount = rowCount;
    this.columnCount = columnCount;
  }

  public String getName() {
    return name;
  }

  public int getIndex() {
    return index;
  }

  public int getRowCount() {
    return rowCount;
  }

  public int getColumnCount() {
    return columnCount;
  }

  /**
   * 获取Excel文件的所有Sheet信息
   *
   * @param fileName 文件路径
   * @return Sheet信息列表
   */
  public static List<ExcelSheetInfo> getSheetInfoList(String fileName) {
    try (InputStream is = new FileInputStream(fileName);
        Workbook workbook = WorkbookFactory.create(is)) {
      return getSheetInfoList(workbook);
    } catch (IOException e) {
      throw new ExcelReadException(
          "读取Sheet信息失败: fileName=" + fileName + ", error=" + e.getMessage(), e);
    }
  }

  /**
   * 获取Excel文件的所有Sheet信息
   *
   * @param file 文件对象
   * @return Sheet信息列表
   */
  public static List<ExcelSheetInfo> getSheetInfoList(File file) {
    try (InputStream is = new FileInputStream(file);
        Workbook workbook = WorkbookFactory.create(is)) {
      return getSheetInfoList(workbook);
    } catch (IOException e) {
      throw new ExcelReadException(
          "读取Sheet信息失败: file=" + file.getName() + ", error=" + e.getMessage(), e);
    }
  }

  /**
   * 获取Excel文件的Sheet数量
   *
   * @param fileName 文件路径
   * @return Sheet数量
   */
  public static int getSheetCount(String fileName) {
    return getSheetInfoList(fileName).size();
  }

  /**
   * 获取Excel文件的Sheet名称列表
   *
   * @param fileName 文件路径
   * @return Sheet名称列表
   */
  public static List<String> getSheetNames(String fileName) {
    List<ExcelSheetInfo> infos = getSheetInfoList(fileName);
    List<String> names = new ArrayList<>(infos.size());
    for (ExcelSheetInfo info : infos) {
      names.add(info.getName());
    }
    return names;
  }

  private static List<ExcelSheetInfo> getSheetInfoList(Workbook workbook) {
    int sheetCount = workbook.getNumberOfSheets();
    List<ExcelSheetInfo> list = new ArrayList<>(sheetCount);
    for (int i = 0; i < sheetCount; i++) {
      Sheet sheet = workbook.getSheetAt(i);
      int rowCount = sheet.getLastRowNum() + 1;
      int colCount = 0;
      if (sheet.getRow(0) != null) {
        colCount = sheet.getRow(0).getLastCellNum();
      }
      list.add(new ExcelSheetInfo(sheet.getSheetName(), i, rowCount, colCount));
    }
    return list;
  }

  @Override
  public String toString() {
    return "ExcelSheetInfo{name='"
        + name
        + "', index="
        + index
        + ", rows="
        + rowCount
        + ", cols="
        + columnCount
        + "}";
  }
}
