package com.njydsz.common.excel.core;

import java.io.IOException;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.excel.annotation.ExcelDataValidation;
import com.njydsz.common.excel.annotation.ExcelIgnore;
import com.njydsz.common.excel.annotation.ExcelMerge;
import com.njydsz.common.excel.annotation.ExcelProperty;
import com.njydsz.common.excel.annotation.ExcelSheet;
import com.njydsz.common.excel.core.config.ExcelConfig;
import com.njydsz.common.excel.core.metadata.WriteMetadata;
import com.njydsz.common.excel.core.metadata.WriteMetadata.WriteHeaderProperty;
import com.njydsz.common.excel.core.metrics.ExcelMetrics;
import com.njydsz.common.excel.core.postprocess.DataValidationHelper;
import com.njydsz.common.excel.core.postprocess.DataValidationHelper.DataValidation;
import com.njydsz.common.excel.core.postprocess.MergeCellHelper;
import com.njydsz.common.excel.core.util.ColumnOrderResolver;
import com.njydsz.common.excel.core.writer.SuperFastExcelWriter;
import com.njydsz.common.excel.exception.ExcelWriteException;
import com.njydsz.common.excel.support.mh.MHFieldAccessor;

/**
 * Excel 写入器 — 统一门面（零 POI 依赖）。
 *
 * <p>自 v26.10.01 起，底层完全委托 {@link SuperFastExcelWriter}，不再依赖 Apache POI。
 * 提供链式 API（sheet、head、freezePane 等），全部能力由 SuperFastExcelWriter 实现：
 *
 * <ul>
 *   <li>类型化写入：基于 {@code @ExcelProperty} 注解的 POJO → xlsx 映射</li>
 *   <li>动态表头：{@code head(List<String>)} + {@code List<List<Object>>} 数据</li>
 *   <li>冻结窗格：{@code freezePane(row, col)}</li>
 *   <li>合并区域：{@code @ExcelSheet.mergedRegions()}</li>
 *   <li>自动列宽：{@code autoColumnWidth(true)}</li>
 * </ul>
 *
 * <h3>不支持的能力（需要 POI，已移除）</h3>
 *
 * <ul>
 *   <li>WriteLifecycleHandler 回调：不再触发</li>
 *   <li>{@code @ExcelStyle} / {@code @ContentStyle} 样式注解：不再应用</li>
 *   <li>XLS (.xls) 格式：仅支持 .xlsx</li>
 *   <li>追加模式（append）：不再支持，调用抛 {@link UnsupportedOperationException}</li>
 *   <li>多 Sheet 同文件写入：请使用 {@link ExcelFacade#writeMultiSheet(java.io.OutputStream)}</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 *
 * <pre>{@code
 * ExcelFacade.write("output.xlsx", User.class)
 *     .sheet("用户")
 *     .freezePane(1, 0)
 *     .autoColumnWidth(true)
 *     .doWrite(userList);
 *
 * // 动态表头
 * ExcelFacade.write(baos)
 *     .head(Arrays.asList("姓名", "年龄"))
 *     .sheet("用户")
 *     .doWrite(Arrays.asList(
 *         Arrays.asList("张三", 25),
 *         Arrays.asList("李四", 30)
 *     ));
 * }</pre>
 *
 * @see ExcelFacade
 * @see SuperFastExcelWriter
 * @see WriteMetadata
 * @author ydsz-team
 * @since 26.09.01
 */
public class ExcelWriter implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(ExcelWriter.class);

  /** 写入配置元数据 */
  private final WriteMetadata metadata;

  /** 是否追加写入模式（已废弃，调用时抛异常） */
  private boolean isAppend;

  /** 是否已调用过 doWrite */
  private boolean isWriteCompleted = false;

  /**
   * 构造方法。
   *
   * @param metadata 写入配置元数据，包含目标路径、映射类型等信息
   */
  public ExcelWriter(WriteMetadata metadata) {
    this.metadata = metadata;
  }

  // ==================== 链式配置方法 ====================

  /**
   * 使用默认配置（创建名为 "sheet1" 的 Sheet）。
   *
   * @return 当前写入器实例，支持链式调用
   */
  public ExcelWriter sheet() {
    return sheet("sheet1");
  }

  /**
   * 指定 Sheet 名称。
   *
   * @param sheetName Sheet 名称
   * @return 当前写入器实例
   */
  public ExcelWriter sheet(String sheetName) {
    metadata.setSheetName(sheetName);
    return this;
  }

  /**
   * 指定 Sheet 序号（创建多个 Sheet 时使用）。
   *
   * <p>注意：自 v26.10.01 起，多 Sheet 写入请使用 {@link ExcelFacade#writeMultiSheet(java.io.OutputStream)}。
   * 本方法保留仅用于单 Sheet 写入时的序号标记。
   *
   * @param sheetNo Sheet 序号
   * @return 当前写入器实例
   */
  public ExcelWriter sheetNo(int sheetNo) {
    metadata.setSheetNo(sheetNo);
    return this;
  }

  /**
   * 创建新 Sheet 并返回新的写入器。
   *
   * <p>自 v26.10.01 起不再共享底层 Workbook，每个 newSheet() 生成独立的写入器。
   * 注意：各写入器的 doWrite 产生独立的 xlsx 文件（写入同一文件路径时后者覆盖前者）。
   * 需要生成多 Sheet 单一文件，请使用 {@link ExcelFacade#writeMultiSheet(java.io.OutputStream)}。
   *
   * @param sheetName 新 Sheet 的名称
   * @return 新的 ExcelWriter 实例，关联到新 Sheet
   */
  public ExcelWriter newSheet(String sheetName) {
    WriteMetadata newMetadata = copyMetadata();
    newMetadata.setSheetName(sheetName);
    newMetadata.setSheetNo(metadata.getSheetNo() != null ? metadata.getSheetNo() + 1 : 0);
    newMetadata.setHeadRowNumber(1);
    return new ExcelWriter(newMetadata);
  }

  /**
   * 复制当前元数据。
   *
   * @return 新的 WriteMetadata 副本
   */
  private WriteMetadata copyMetadata() {
    WriteMetadata newMetadata = new WriteMetadata();
    newMetadata.setClazz(metadata.getClazz());
    newMetadata.setFilePath(metadata.getFilePath());
    newMetadata.setFile(metadata.getFile());
    newMetadata.setOutputStream(metadata.getOutputStream());
    newMetadata.setHeadRowNumber(metadata.getHeadRowNumber());
    newMetadata.setDateFormat(metadata.getDateFormat());
    newMetadata.setNumberFormat(metadata.getNumberFormat());
    newMetadata.setIsAutomaticTrim(metadata.getIsAutomaticTrim());
    newMetadata.setPassword(metadata.getPassword());
    newMetadata.setExcludeColumnFiledNames(metadata.getExcludeColumnFiledNames());
    newMetadata.setIncludeColumnFiledNames(metadata.getIncludeColumnFiledNames());
    newMetadata.setFreezePaneRow(metadata.getFreezePaneRow());
    newMetadata.setFreezePaneCol(metadata.getFreezePaneCol());
    newMetadata.setIsAutoColumnWidth(metadata.getIsAutoColumnWidth());
    newMetadata.setMergedRegions(metadata.getMergedRegions());
    // 传播后处理器和注解扫描状态（避免 newSheet 时重复注册注解处理器）
    newMetadata.setPostProcessors(metadata.getPostProcessors());
    newMetadata.setScannedAnnotations(metadata.getScannedAnnotations());
    return newMetadata;
  }

  /**
   * 指定表头行号。
   *
   * <p>表头行号从 1 开始计数（1 = 表头写在第一行，数据从第二行开始），与
   * {@code ExcelReader.headRowNumber} 及 {@code @ExcelSheet.headRowNumber} 语义一致。
   *
   * @param headRowNumber 表头行号（从 1 开始计数）
   * @return 当前写入器实例
   */
  public ExcelWriter headRowNumber(int headRowNumber) {
    metadata.setHeadRowNumber(headRowNumber);
    return this;
  }

  /**
   * 使用 1904 日期窗口（Mac Excel 兼容）。
   *
   * <p>注意：需在构建 ExcelConfig 时设置，本方法仅保留接口兼容性。
   *
   * @return 当前写入器实例
   * @deprecated 自 v26.10.01 起无效。日期窗口配置通过 {@link ExcelConfig.Builder#use1904Windowing(boolean)} 设置
   */
  @Deprecated(forRemoval = true)
  public ExcelWriter use1904Windowing() {
    LOG.warn("ExcelConfig 为不可变对象，use1904Windowing 设置应在构建配置时完成");
    return this;
  }

  /**
   * 设置日期格式。
   *
   * <p>用于格式化 Date 类型字段的输出。
   *
   * @param dateFormat 日期格式，如 "yyyy-MM-dd"
   * @return 当前写入器实例
   */
  public ExcelWriter dateFormat(String dateFormat) {
    metadata.setDateFormat(dateFormat);
    return this;
  }

  /**
   * 设置数字格式。
   *
   * @param numberFormat 数字格式，如 "#,##0.00"
   * @return 当前写入器实例
   */
  public ExcelWriter numberFormat(String numberFormat) {
    metadata.setNumberFormat(numberFormat);
    return this;
  }

  /**
   * 设置 Sheet 保护密码。
   *
   * <p>注意：当前版本不支持密码保护，调用仅保留接口兼容性。
   *
   * @param password 保护密码
   * @return 当前写入器实例
   * @deprecated 自 v26.10.01 起无效，当前版本不支持密码保护
   */
  @Deprecated(forRemoval = true)
  public ExcelWriter password(String password) {
    LOG.warn("当前版本不支持 Sheet 密码保护");
    metadata.setPassword(password);
    return this;
  }

  /**
   * 设置冻结窗格。
   *
   * <p>用于固定表头或首列，方便查看大数据量时的滚动浏览。
   * 例如 {@code freezePane(1, 0)} 冻结首行，{@code freezePane(0, 1)} 冻结首列。
   *
   * @param row 冻结的行数（从首行开始），0 表示不冻结行
   * @param col 冻结的列数（从首列开始），0 表示不冻结列
   * @return 当前写入器实例
   */
  public ExcelWriter freezePane(int row, int col) {
    metadata.setFreezePaneRow(row);
    metadata.setFreezePaneCol(col);
    return this;
  }

  /**
   * 设置自动调整列宽。
   *
   * <p>设置为 true 时，在写入过程中实时估算每列最大内容宽度，
   * 导出后 Excel 无需手动调整列宽即可完整显示内容。
   *
   * <p>注意：该功能会带来少量内存开销（缓冲 sheet XML 内容以计算列宽），
   * 超大文件（50 万行+）场景建议关闭以降低内存使用。
   *
   * @param autoColumnWidth true 表示自动调整列宽
   * @return 当前写入器实例
   */
  public ExcelWriter autoColumnWidth(boolean autoColumnWidth) {
    metadata.setIsAutoColumnWidth(autoColumnWidth);
    return this;
  }

  /**
   * 设置是否自动去除字符串首尾空格。
   *
   * @param automaticTrim true 启用自动去空格，默认 true
   * @return 当前写入器实例
   */
  public ExcelWriter automaticTrim(boolean automaticTrim) {
    metadata.setIsAutomaticTrim(automaticTrim);
    return this;
  }

  /**
   * 设置 Excel 全局配置。
   *
   * @param config Excel 全局配置
   * @return 当前写入器实例
   */
  public ExcelWriter config(ExcelConfig config) {
    metadata.setExcelConfig(config);
    return this;
  }

  /**
   * 追加写入模式（已废弃）。
   *
   * <p>当前版本不支持追加模式，调用将抛出 {@link UnsupportedOperationException}。
   *
   * @return 当前写入器实例
   * @throws UnsupportedOperationException 始终抛出
   * @deprecated 自 v26.10.01 起不支持。替代方案：{@link ExcelFacade#writeMultiSheet(java.io.OutputStream)}
   */
  @Deprecated(forRemoval = true)
  public ExcelWriter append() {
    throw new UnsupportedOperationException(
        "追加模式已不支持。如需增量写入，请使用 MultiSheetFastWriter 或重新构建完整数据后写入。");
  }

  // ==================== 列过滤配置 ====================

  /**
   * 排除指定字段。
   *
   * @param excludeColumnFiledNames 要排除的字段名集合
   * @return 当前写入器实例
   */
  public ExcelWriter excludeColumnFiledNames(Set<String> excludeColumnFiledNames) {
    metadata.setExcludeColumnFiledNames(excludeColumnFiledNames);
    return this;
  }

  /**
   * 排除指定字段。
   *
   * @param excludeColumnFiledNames 要排除的字段名数组
   * @return 当前写入器实例
   */
  public ExcelWriter excludeColumnFiledNames(String... excludeColumnFiledNames) {
    Set<String> set = new HashSet<>(Arrays.asList(excludeColumnFiledNames));
    return excludeColumnFiledNames(set);
  }

  /**
   * 只包含指定字段。
   *
   * @param includeColumnFiledNames 要包含的字段名集合
   * @return 当前写入器实例
   */
  public ExcelWriter includeColumnFiledNames(Set<String> includeColumnFiledNames) {
    metadata.setIncludeColumnFiledNames(includeColumnFiledNames);
    return this;
  }

  /**
   * 只包含指定字段。
   *
   * @param includeColumnFiledNames 要包含的字段名数组
   * @return 当前写入器实例
   */
  public ExcelWriter includeColumnFiledNames(String... includeColumnFiledNames) {
    Set<String> set = new HashSet<>(Arrays.asList(includeColumnFiledNames));
    return includeColumnFiledNames(set);
  }

  // ==================== 动态表头 ====================

  /**
   * 设置动态表头（无需映射类）。
   *
   * <p>当不使用注解映射类时，可通过此方法设置表头列表。
   * 配合 {@link #doWrite(Object)} 写入 {@code List<List<Object>>} 或
   * {@code List<Map<String, Object>>} 数据。
   *
   * @param headers 表头名称列表
   * @return 当前写入器实例
   */
  public ExcelWriter head(List<String> headers) {
    List<WriteHeaderProperty> headList = new ArrayList<>(16);
    for (int i = 0; i < headers.size(); i++) {
      WriteHeaderProperty property = new WriteHeaderProperty();
      property.setName(headers.get(i));
      property.setColumnIndex(i);
      headList.add(property);
    }
    metadata.setHeadList(headList);
    return this;
  }

  // ==================== 核心写入方法 ====================

  /**
   * 执行写入（写入到默认 Sheet）。
   *
   * @param data 要写入的数据，支持 List、数组或单个对象
   */
  public void doWrite(Object data) {
    doWrite(data, 0);
  }

  /**
   * 执行写入（写入到指定 Sheet 序号）。
   *
   * <p>核心写入方法，全部委托 {@link SuperFastExcelWriter} 执行：
   *
   * <ol>
   *   <li>解析类注解构建表头（或使用动态表头）</li>
   *   <li>直接生成 OOXML（.xlsx）字节流，零 POI 对象模型开销</li>
   *   <li>输出到 file/fileStream 目标</li>
   * </ol>
   *
   * @param data 要写入的数据
   * @param sheetNo Sheet序号
   */
  public void doWrite(Object data, int sheetNo) {
    long startTime = System.nanoTime();
    int rowCount = (data instanceof List) ? ((List<?>) data).size() : 1;
    try {
      if (metadata.getDataSize() == null && data instanceof List) {
        metadata.setDataSize(((List<?>) data).size());
      }

      // 应用 @ExcelSheet 注解配置到 metadata
      applyExcelSheetAnnotation();

      // 扫描 @ExcelMerge / @ExcelDataValidation 注解并自动注册后处理器
      registerPostProcessorsFromAnnotations();

      // 委托 SuperFastExcelWriter 完成全部写入
      SuperFastExcelWriter fastWriter = new SuperFastExcelWriter(metadata);
      fastWriter.doWrite(data);

      ExcelMetrics.recordWrite(
          Duration.ofNanos(System.nanoTime() - startTime), rowCount, "super_fast", true);
      isWriteCompleted = true;

    } catch (Exception e) {
      LOG.error("Excel 写入异常", e);
      ExcelMetrics.recordWrite(
          Duration.ofNanos(System.nanoTime() - startTime), rowCount, "super_fast", false);
      throw ExcelWriteException.dataWriteFailed(0, null, null, e);
    }
  }

  /**
   * 将 @ExcelSheet 注解的配置信息应用到 WriteMetadata。
   *
   * <p>包括：Sheet 名称、表头行号、日期格式、冻结窗格、自动列宽、合并区域。
   */
  private void applyExcelSheetAnnotation() {
    Class<?> clazz = metadata.getClazz();
    if (clazz == null) {
      return;
    }
    ExcelSheet sheetAnnotation = clazz.getAnnotation(ExcelSheet.class);
    if (sheetAnnotation == null) {
      return;
    }
    if (!sheetAnnotation.name().isEmpty()) {
      metadata.setSheetName(sheetAnnotation.name());
    }
    if (sheetAnnotation.headRowNumber() > 0) {
      metadata.setHeadRowNumber(sheetAnnotation.headRowNumber());
    }
    if (!sheetAnnotation.dateFormat().isEmpty()) {
      metadata.setDateFormat(sheetAnnotation.dateFormat());
    }
    metadata.setFreezePaneRow(sheetAnnotation.freezePane().row());
    metadata.setFreezePaneCol(sheetAnnotation.freezePane().col());
    metadata.setIsAutoColumnWidth(sheetAnnotation.autoColumnWidth());

    ExcelSheet.MergedRegion[] mergedRegions = sheetAnnotation.mergedRegions();
    if (mergedRegions != null && mergedRegions.length > 0) {
      List<int[]> regionList = new ArrayList<>(16);
      for (ExcelSheet.MergedRegion region : mergedRegions) {
        regionList.add(new int[] {
            region.startRow(), region.endRow(),
            region.startCol(), region.endCol()
        });
      }
      metadata.setMergedRegions(regionList);
    }
  }

  /**
   * 扫描 POJO 类上的 {@code @ExcelMerge} 和 {@code @ExcelDataValidation} 注解，
   * 自动为匹配字段注册对应的 {@link com.njydsz.common.excel.core.postprocess.XlsxPostProcessor}。
   *
   * <p>已在 metadata 中手动注册过后处理器时，注解扫描结果会追加到已有列表末尾（不重复添加同类型）。
   * 本方法幂等：第二次调用不会重复注册。
   *
   * <h3>列位置解析</h3>
   *
   * <p>字段到列位置的映射遵循与写入管线一致的规则（{@link ColumnOrderResolver}）：
   *
   * <ul>
   *   <li>有显式 {@code @ExcelProperty(index=N)} 时，列位置 = N</li>
   *   <li>否则按 order 优先级 / 声明顺序决定列位置</li>
   * </ul>
   *
   * <p>合并单元格仅合并连续的同值行区间，空行自动打断。
   * 数据验证的 sqref 使用列字母 + 整列行号范围（C2:C1048576），适配 Excel 最大行数。
   */
  private void registerPostProcessorsFromAnnotations() {
    Class<?> clazz = metadata.getClazz();
    if (clazz == null) {
      return;
    }

    // 幂等：扫描一次后标记，避免重复注册
    if (metadata.getScannedAnnotations()) {
      return;
    }
    metadata.setScannedAnnotations(true);

    // 字段 -> 列位置映射
    List<Field> orderedFields = ColumnOrderResolver.resolveOrderedFields(clazz);
    if (orderedFields.isEmpty()) {
      return;
    }

    // 构建 field -> colIndex 映射（显式 index 优先，否则用顺序位置）
    // 先处理显式 index 的字段
    java.util.Map<Field, Integer> fieldToCol = new java.util.IdentityHashMap<>(orderedFields.size());
    java.util.Set<Integer> claimedCols = new java.util.HashSet<>();
    for (Field f : orderedFields) {
      ExcelProperty prop = f.getAnnotation(ExcelProperty.class);
      if (prop != null && prop.index() >= 0) {
        fieldToCol.put(f, prop.index());
        claimedCols.add(prop.index());
      }
    }
    // 再填充没有显式 index 的字段（跳过已被显式占用的列）
    int implicitCol = 0;
    for (Field f : orderedFields) {
      if (fieldToCol.containsKey(f)) {
        continue;
      }
      while (claimedCols.contains(implicitCol)) {
        implicitCol++;
      }
      fieldToCol.put(f, implicitCol);
      claimedCols.add(implicitCol);
      implicitCol++;
    }

    // 收集合并列和验证规则
    final java.util.List<Integer> mergeCols = new java.util.ArrayList<>();
    final java.util.List<DataValidation> validations = new java.util.ArrayList<>();

    for (Field field : orderedFields) {
      Integer col = fieldToCol.get(field);
      if (col == null) {
        continue;
      }

      // @ExcelMerge
      if (field.isAnnotationPresent(ExcelMerge.class)) {
        mergeCols.add(col);
      }

      // @ExcelDataValidation
      ExcelDataValidation dv = field.getAnnotation(ExcelDataValidation.class);
      if (dv != null && dv.type() != ExcelDataValidation.ValidationType.ANY) {
        String colLetter = toColumnLetter(col);
        // 整列 sqref：跳过第 1 行（表头），应用到最大行
        String sqref = colLetter + "2:" + colLetter + "1048576";
        DataValidation dvEntry = buildDataValidation(dv, sqref);
        if (dvEntry != null) {
          validations.add(dvEntry);
        }
      }
    }

    // 注册后处理器（合并与验证对 sheet 0 生效；多 Sheet 可通过手动 addPostProcessor 指定其他 sheet）
    if (!mergeCols.isEmpty()) {
      metadata.addPostProcessor((is, os) -> MergeCellHelper.apply(is, os, config -> {
        MergeCellHelper.SheetConfig sc = config.sheet(0);
        for (int c : mergeCols) {
          sc.mergeColumn(c);
        }
      }));
    }

    if (!validations.isEmpty()) {
      metadata.addPostProcessor((is, os) -> DataValidationHelper.apply(is, os,
          (sheetIndex, vs) -> {
            // 仅对第一个 Sheet 应用注解验证，其余 sheet 由手动注册或忽略
            if (sheetIndex == 0) {
              vs.addAll(validations);
            }
          }));
    }
  }

  /**
   * 根据 {@code @ExcelDataValidation} 注解配置构建 {@link DataValidation} 实例。
   *
   * <p>使用 {@link DataValidationHelper} 的静态工厂方法创建基础对象，
   * 然后补充错误/提示文案等公共属性。返回 {@code null} 表示类型未识别（ANY）。
   */
  private static DataValidation buildDataValidation(ExcelDataValidation anno, String sqref) {
    ExcelDataValidation.ValidationType type = anno.type();
    DataValidation v;

    switch (type) {
      case LIST:
        v = DataValidationHelper.listValidation(sqref, anno.formula1());
        v.showDropdown = anno.showDropdown();
        break;
      case INTEGER:
      case DECIMAL:
      case DATE:
      case TIME:
        v = DataValidationHelper.rangeValidation(sqref, type.ooxmlType,
            anno.operator().ooxmlOperator, anno.formula1(), anno.formula2());
        break;
      case LENGTH:
        v = DataValidationHelper.lengthValidation(sqref, anno.formula1(), anno.formula2());
        break;
      case CUSTOM:
        v = DataValidationHelper.customValidation(sqref, anno.formula1());
        break;
      default:
        return null; // ANY / 未识别
    }

    // 补充公共字段（错误/提示文案、样式）
    v.ignoreBlank = anno.ignoreBlank();
    v.errorStyle = anno.errorStyle().value;
    v.errorTitle = anno.errorTitle();
    v.errorContent = anno.errorMessage();
    v.promptTitle = anno.promptTitle();
    v.promptContent = anno.promptContent();
    return v;
  }

  /**
   * 列索引转 Excel 列字母（0 → A, 1 → B, ..., 25 → Z, 26 → AA）。
   */
  static String toColumnLetter(int col) {
    if (col < 0) {
      throw new IllegalArgumentException("Column index must be >= 0: " + col);
    }
    StringBuilder sb = new StringBuilder();
    int n = col;
    while (n >= 0) {
      sb.append((char) ('A' + (n % 26)));
      n = n / 26 - 1;
      if (n < 0) {
        break;
      }
    }
    return sb.reverse().toString();
  }

  /**
   * 检查是否可以进行写入。
   *
   * @return {@code true} 如果可以写入，{@code false} 如果已经完成过写入
   */
  boolean canWrite() {
    return !isWriteCompleted;
  }

  /**
   * 完成写入并释放资源。
   *
   * <p>当前版本 SuperFastExcelWriter 在 doWrite 中已自行完成输出和清理，
   * 本方法保留仅为兼容原有调用方惯用写法（幂等无操作）。
   *
   * @throws IOException 不会抛出
   * @deprecated 自 v26.10.01 起无需调用，SuperFastExcelWriter 在 doWrite 中自动完成输出
   */
  @Deprecated(forRemoval = true)
  public void finish() throws IOException {
    // SuperFastExcelWriter 在 doWrite 中已完成全部输出操作
    // 无需额外 finish 步骤，保留此方法仅为兼容调用方惯用写法
    isWriteCompleted = true;
  }

  /**
   * 关闭写入器（AutoCloseable 实现）。
   *
   * <p>当前版本 SuperFastExcelWriter 在 doWrite 中已自行完成输出和清理，
   * 本方法为幂等无操作，仅用于支持 try-with-resources 语法。
   */
  @Override
  public void close() {
    // SuperFastExcelWriter 在 doWrite 中已完成全部输出操作
    // 无需额外关闭步骤，标记写入已完成
    isWriteCompleted = true;
  }

  /**
   * 超高速批量写入（已废弃）。
   *
   * <p>自 v26.10.01 起，直接调用 {@link #doWrite(Object)} 即可获得最优性能，
   * 本方法保留仅为兼容原有调用方。
   *
   * @param dataList 数据列表
   * @deprecated 自 v26.10.01 起直接调用 {@link #doWrite(Object)} 即可
   */
  @Deprecated(forRemoval = true)
  public void writeBatch(List<?> dataList) {
    doWrite(dataList);
  }

  /**
   * 临时兼容方法 — 设置多 Sheet 写入模式（已废弃）。
   *
   * @param multiSheet 是否多Sheet写入（被忽略）
   * @return 当前写入器实例
   * @deprecated 自 v26.10.01 起无效，使用 {@link ExcelFacade#writeMultiSheet(java.io.OutputStream)}
   */
  @Deprecated(forRemoval = true)
  public ExcelWriter setMultiSheetWriting(boolean multiSheet) {
    if (multiSheet) {
      LOG.warn("多 Sheet 共享 Workbook 模式已废弃，请使用 ExcelFacade.writeMultiSheet(OutputStream)");
    }
    return this;
  }

  /**
   * 清空日期格式化缓存。
   *
   * @deprecated 自 v26.10.01 起无需调用，已无内部缓存
   */
  @Deprecated(forRemoval = true)
  public static void clearDateFormatCache() {
    // 已无缓存需要清空，保留此方法仅为兼容调用
  }
}
