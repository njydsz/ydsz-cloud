package com.njydsz.common.excel.core.writer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.excel.core.config.ExcelConfig;
import com.njydsz.common.excel.core.context.WriteContext;
import com.njydsz.common.excel.core.metadata.WriteMetadata;
import com.njydsz.common.excel.core.style.WriteStyleHandler;

/**
 * 工作簿工厂 - 负责创建和初始化Workbook实例
 *
 * <p>根据文件扩展名判断格式(.xlsx/.xls)，支持追加模式打开已有文件。 追加模式时会读取已有文件并定位到数据末尾。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see ExcelWriter
 */
public class WorkbookFactory {

  private static final Logger LOG = LoggerFactory.getLogger(WorkbookFactory.class);

  /** 初始化工作簿的返回结果 */
  public static class WorkbookInitResult {
    private final Workbook workbook;
    private final Sheet sheet;
    private final int currentRowIndex;
    private final WriteStyleHandler styleHandler;

    public WorkbookInitResult(
        Workbook workbook, Sheet sheet, int currentRowIndex, WriteStyleHandler styleHandler) {
      this.workbook = workbook;
      this.sheet = sheet;
      this.currentRowIndex = currentRowIndex;
      this.styleHandler = styleHandler;
    }

    public Workbook getWorkbook() {
      return workbook;
    }

    public Sheet getSheet() {
      return sheet;
    }

    public int getCurrentRowIndex() {
      return currentRowIndex;
    }

    public WriteStyleHandler getStyleHandler() {
      return styleHandler;
    }
  }

  /**
   * 初始化工作簿
   *
   * <p>根据文件扩展名判断格式:
   *
   * <ul>
   *   <li>.xlsx -> SXSSFWorkbook(流式写入,低内存) 或 XSSFWorkbook(小数据量)
   *   <li>.xls -> HSSFWorkbook(传统写入)
   * </ul>
   *
   * <p>追加模式时:
   *
   * <ul>
   *   <li>如果文件存在,则打开已有工作簿
   *   <li>获取或创建目标Sheet
   *   <li>设置起始行为Sheet最后一行+1
   * </ul>
   *
   * @param metadata 写入元数据
   * @param context 写入上下文
   * @param existingWorkbook 已有的工作簿(多Sheet场景),可为null
   * @param append 是否追加模式
   * @return 工作簿初始化结果
   * @throws IOException 文件创建异常
   */
  public WorkbookInitResult initWorkbook(
      WriteMetadata metadata, WriteContext context, Workbook existingWorkbook, boolean append)
      throws IOException {
    if (existingWorkbook != null) {
      return new WorkbookInitResult(existingWorkbook, context.getSheet(), 0, null);
    }

    String filePath = metadata.getFilePath();

    boolean isXlsx = true;
    if (filePath != null) {
      isXlsx = !filePath.toLowerCase().endsWith(".xls");
    }

    if (append && filePath != null) {
      File existingFile = new File(filePath);
      if (existingFile.exists()) {
        if (isXlsx) {
          XSSFWorkbook xssfWorkbook;
          try (FileInputStream fis = new FileInputStream(existingFile)) {
            xssfWorkbook = new XSSFWorkbook(fis);
            int sheetIndex = getOrCreateSheetIndex(xssfWorkbook, metadata);
            XSSFSheet sourceSheet = xssfWorkbook.getSheetAt(sheetIndex);
            Sheet sheet;
            if (sourceSheet == null) {
              sheet = xssfWorkbook.createSheet(metadata.getSheetName());
            } else {
              sheet = sourceSheet;
            }
            int currentRowIndex = findLastRowIndex(sheet, metadata) + 1;
            if (metadata.getPassword() != null && !metadata.getPassword().isEmpty()) {
              sheet.protectSheet(metadata.getPassword());
            }
            context.setSheet(sheet);
            WriteStyleHandler styleHandler = new WriteStyleHandler(xssfWorkbook);
            return new WorkbookInitResult(xssfWorkbook, sheet, currentRowIndex, styleHandler);
          }
        } else {
          HSSFWorkbook hssfWorkbook;
          // 使用 try-with-resources 确保 FileInputStream 正确关闭（符合云顶规范 20.1.1）
          try (FileInputStream fis = new FileInputStream(existingFile)) {
            hssfWorkbook = new HSSFWorkbook(fis);
          }
          int sheetIndex = getOrCreateSheetIndex(hssfWorkbook, metadata);
          HSSFSheet sourceSheet = hssfWorkbook.getSheetAt(sheetIndex);
          Sheet sheet;
          if (sourceSheet == null) {
            sheet = hssfWorkbook.createSheet(metadata.getSheetName());
          } else {
            sheet = sourceSheet;
          }
          int currentRowIndex = findLastRowIndex(sheet, metadata) + 1;
          if (metadata.getPassword() != null && !metadata.getPassword().isEmpty()) {
            sheet.protectSheet(metadata.getPassword());
          }
          context.setSheet(sheet);
          WriteStyleHandler styleHandler = new WriteStyleHandler(hssfWorkbook);
          return new WorkbookInitResult(hssfWorkbook, sheet, currentRowIndex, styleHandler);
        }
      }
    }

    ExcelConfig excelConfig =
        metadata.getExcelConfig() != null ? metadata.getExcelConfig() : ExcelConfig.defaults();
    int cacheSize = excelConfig.getWriteCacheSize();
    Integer dataSize = metadata.getDataSize();

    Workbook workbook;
    if (isXlsx) {
      if (dataSize != null && dataSize < cacheSize) {
        workbook = new XSSFWorkbook();
        LOG.debug("数据量较小 ({} < {}),使用 XSSFWorkbook", dataSize, cacheSize);
      } else {
        int windowSize = excelConfig.getWriteCacheSize();
        workbook = new SXSSFWorkbook(windowSize);
        ((SXSSFWorkbook) workbook).setCompressTempFiles(true);
        LOG.debug("使用 SXSSFWorkbook 流式写入，窗口大小：{}行", windowSize);
      }
    } else {
      workbook = new HSSFWorkbook();
    }

    Sheet sheet = workbook.createSheet(metadata.getSheetName());
    if (metadata.getPassword() != null && !metadata.getPassword().isEmpty()) {
      sheet.protectSheet(metadata.getPassword());
    }

    context.setSheet(sheet);
    WriteStyleHandler styleHandler = new WriteStyleHandler(workbook);
    return new WorkbookInitResult(workbook, sheet, 0, styleHandler);
  }

  /**
   * 获取或创建Sheet的索引
   *
   * @param sourceWorkbook 源工作簿
   * @param metadata 写入元数据
   * @return Sheet索引
   */
  public int getOrCreateSheetIndex(Workbook sourceWorkbook, WriteMetadata metadata) {
    String sheetName = metadata.getSheetName();
    Integer sheetNo = metadata.getSheetNo();

    if (sheetNo != null && sheetNo >= 0) {
      int index = sheetNo;
      if (index < sourceWorkbook.getNumberOfSheets()) {
        return index;
      }
    }

    if (sheetName != null && !sheetName.isEmpty()) {
      int index = sourceWorkbook.getSheetIndex(sheetName);
      if (index >= 0) {
        return index;
      }
    }

    return 0;
  }

  /**
   * 查找Sheet中最后一行的索引
   *
   * @param sheet Sheet对象
   * @param metadata 写入元数据
   * @return 最后一行的索引
   */
  public int findLastRowIndex(Sheet sheet, WriteMetadata metadata) {
    if (sheet == null) {
      return metadata.getHeadRowNumber() - 1;
    }
    int lastRow = sheet.getLastRowNum();
    if (lastRow < metadata.getHeadRowNumber()) {
      return metadata.getHeadRowNumber() - 1;
    }
    return lastRow;
  }
}
