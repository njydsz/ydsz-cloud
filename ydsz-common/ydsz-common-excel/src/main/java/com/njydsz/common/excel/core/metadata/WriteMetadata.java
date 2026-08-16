package com.njydsz.common.excel.core.metadata;

import com.njydsz.common.excel.annotation.ExcelStyle;
import com.njydsz.common.excel.core.config.ExcelConfig;
import com.njydsz.common.excel.support.asm.ASMFieldAccessor;
import java.io.File;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 写入元数据 - 封装Excel写入配置信息
 *
 * <p>包含写入Excel所需的所有配置参数,如文件路径、映射类型、Sheet信息等。 在写入过程中被持续使用,控制输出的格式和行为。
 *
 * <h3>配置项分类</h3>
 *
 * <ul>
 *   <li>输出目标 - filePath、file、outputStream(三选一)
 *   <li>Sheet配置 - sheetName、sheetNo、headRowNumber
 *   <li>映射配置 - clazz、headList
 *   <li>格式配置 - dateFormat、numberFormat
 *   <li>保护配置 - password、passwordProtected
 * </ul>
 *
 * @see ExcelWriter
 * @see ReadMetadata
 * @author ydsz-team
 * @since 1.0.0
 */
public class WriteMetadata {

  /** 默认表头行号 */
  public static final int DEFAULT_HEAD_ROW_NUMBER = 1;

  /** 数据库导入场景的表头行号 */
  public static final int DEFAULT_DATABASE_HEAD_ROW_NUMBER = 0;

  /** Excel 全局配置 */
  private ExcelConfig excelConfig;

  /** 映射的源类类型 */
  private Class<?> clazz;

  /** 文件路径 */
  private String filePath;

  /** File对象 */
  private File file;

  /** 输出流 */
  private OutputStream outputStream;

  /** Sheet名称 */
  private String sheetName;

  /** Sheet序号 */
  private Integer sheetNo;

  /** 表头行号 */
  private Integer headRowNumber;

  /** 数据起始行号(预留) */
  private Integer dataRowNumber;

  /** 是否使用科学计数法 */
  private Boolean useScientificNotation;

  /** 日期格式 */
  private String dateFormat;

  /** 数字格式 */
  private String numberFormat;

  /** Sheet保护密码 */
  private String password;

  /** 是否锁定(预留) */
  private Boolean locked;

  /** 表头属性列表 */
  private List<WriteHeaderProperty> headList;

  /** 自动去除字符串首尾空格 */
  private Boolean automaticTrim;

  /** 最大列字符长度 */
  private Integer maxChanLength;

  /** Excel类型(xlsx/xls) */
  private String excelType;

  /** 是否写入隐藏Sheet(预留) */
  private Boolean writeHiddenSheet;

  /** 是否密码保护 */
  private Boolean passwordProtected;

  /** 要排除的字段名集合 */
  private Set<String> excludeColumnFiledNames;

  /** 要包含的字段名集合 */
  private Set<String> includeColumnFiledNames;

  /** 数据大小（用于智能选择 Workbook 类型） */
  private Integer dataSize;

  /** 冻结窗格 - 冻结的行数 */
  private Integer freezePaneRow;

  /** 冻结窗格 - 冻结的列数 */
  private Integer freezePaneCol;

  /** 是否自动设置列宽 */
  private Boolean autoColumnWidth;

  /** 合并单元格区域列表: [startRow, endRow, startCol, endCol] */
  private List<int[]> mergedRegions;

  /**
   * 默认构造方法
   *
   * <p>初始化默认值:
   *
   * <ul>
   *   <li>sheetName = "sheet1"
   *   <li>sheetNo = 0
   *   <li>headRowNumber = 1
   *   <li>automaticTrim = true
   *   <li>excelType = "xlsx"
   * </ul>
   */
  public WriteMetadata() {
    this.sheetName = "sheet1";
    this.sheetNo = 0;
    this.headRowNumber = DEFAULT_HEAD_ROW_NUMBER;
    this.dataRowNumber = 0;
    this.useScientificNotation = false;
    this.locked = false;
    this.headList = new ArrayList<>();
    this.automaticTrim = true;
    this.maxChanLength = 1024;
    this.excelType = "xlsx";
    this.writeHiddenSheet = false;
    this.passwordProtected = false;
  }

  public ExcelConfig getExcelConfig() {
    return excelConfig;
  }

  public void setExcelConfig(ExcelConfig excelConfig) {
    this.excelConfig = excelConfig;
  }

  public Class<?> getClazz() {
    return clazz;
  }

  public void setClazz(Class<?> clazz) {
    this.clazz = clazz;
  }

  public String getFilePath() {
    return filePath;
  }

  public void setFilePath(String filePath) {
    this.filePath = filePath;
  }

  public File getFile() {
    return file;
  }

  public void setFile(File file) {
    this.file = file;
  }

  public OutputStream getOutputStream() {
    return outputStream;
  }

  public void setOutputStream(OutputStream outputStream) {
    this.outputStream = outputStream;
  }

  public String getSheetName() {
    return sheetName;
  }

  public void setSheetName(String sheetName) {
    this.sheetName = sheetName;
  }

  public Integer getSheetNo() {
    return sheetNo;
  }

  public void setSheetNo(Integer sheetNo) {
    this.sheetNo = sheetNo;
  }

  public Integer getHeadRowNumber() {
    return headRowNumber;
  }

  public void setHeadRowNumber(Integer headRowNumber) {
    this.headRowNumber = headRowNumber;
  }

  public Integer getDataRowNumber() {
    return dataRowNumber;
  }

  public void setDataRowNumber(Integer dataRowNumber) {
    this.dataRowNumber = dataRowNumber;
  }

  public Boolean getUseScientificNotation() {
    return useScientificNotation;
  }

  public void setUseScientificNotation(Boolean useScientificNotation) {
    this.useScientificNotation = useScientificNotation;
  }

  public String getDateFormat() {
    return dateFormat;
  }

  public void setDateFormat(String dateFormat) {
    this.dateFormat = dateFormat;
  }

  public String getNumberFormat() {
    return numberFormat;
  }

  public void setNumberFormat(String numberFormat) {
    this.numberFormat = numberFormat;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public Boolean getLocked() {
    return locked;
  }

  public void setLocked(Boolean locked) {
    this.locked = locked;
  }

  public List<WriteHeaderProperty> getHeadList() {
    return headList;
  }

  public void setHeadList(List<WriteHeaderProperty> headList) {
    this.headList = headList;
  }

  public Boolean getAutomaticTrim() {
    return automaticTrim;
  }

  public void setAutomaticTrim(Boolean automaticTrim) {
    this.automaticTrim = automaticTrim;
  }

  public Integer getMaxChanLength() {
    return maxChanLength;
  }

  public void setMaxChanLength(Integer maxChanLength) {
    this.maxChanLength = maxChanLength;
  }

  public String getExcelType() {
    return excelType;
  }

  public void setExcelType(String excelType) {
    this.excelType = excelType;
  }

  public Boolean getWriteHiddenSheet() {
    return writeHiddenSheet;
  }

  public void setWriteHiddenSheet(Boolean writeHiddenSheet) {
    this.writeHiddenSheet = writeHiddenSheet;
  }

  public Boolean getPasswordProtected() {
    return passwordProtected;
  }

  public void setPasswordProtected(Boolean passwordProtected) {
    this.passwordProtected = passwordProtected;
  }

  public Set<String> getExcludeColumnFiledNames() {
    return excludeColumnFiledNames;
  }

  public void setExcludeColumnFiledNames(Set<String> excludeColumnFiledNames) {
    this.excludeColumnFiledNames = excludeColumnFiledNames;
  }

  public Set<String> getIncludeColumnFiledNames() {
    return includeColumnFiledNames;
  }

  public void setIncludeColumnFiledNames(Set<String> includeColumnFiledNames) {
    this.includeColumnFiledNames = includeColumnFiledNames;
  }

  public Integer getDataSize() {
    return dataSize;
  }

  public void setDataSize(Integer dataSize) {
    this.dataSize = dataSize;
  }

  public Integer getFreezePaneRow() {
    return freezePaneRow;
  }

  public void setFreezePaneRow(Integer freezePaneRow) {
    this.freezePaneRow = freezePaneRow;
  }

  public Integer getFreezePaneCol() {
    return freezePaneCol;
  }

  public void setFreezePaneCol(Integer freezePaneCol) {
    this.freezePaneCol = freezePaneCol;
  }

  public Boolean getAutoColumnWidth() {
    return autoColumnWidth;
  }

  public void setAutoColumnWidth(Boolean autoColumnWidth) {
    this.autoColumnWidth = autoColumnWidth;
  }

  public List<int[]> getMergedRegions() {
    return mergedRegions;
  }

  public void setMergedRegions(List<int[]> mergedRegions) {
    this.mergedRegions = mergedRegions;
  }

  /**
   * 追加一个表头定义项。
   *
   * <p>追加顺序即默认出列顺序（未显式指定 {@code order} 时）。不做重名或列索引冲突校验， 重复列索引会导致后写入的单元格覆盖先写入的内容。
   *
   * <p><b>前置条件</b>：{@code headList} 由构造方法初始化，但若调用方先用 {@link #setHeadList(List)} 传入了 {@code
   * null}，本方法会抛 {@link NullPointerException}。
   *
   * @param property 表头定义项，不做非空校验，传 {@code null} 会被原样加入集合
   */
  public void addHeadProperty(WriteHeaderProperty property) {
    this.headList.add(property);
  }

  /**
   * 写入表头属性
   *
   * <p>封装Excel表头的相关信息, 包括表头名称、对应字段、列索引、宽度、日期格式等。
   */
  public static class WriteHeaderProperty {

    /** 表头名称 */
    private String name;

    /** 对应的Java字段 */
    private Field field;

    /** ASM字节码生成的FieldGetter - 高性能字段访问器 */
    private transient ASMFieldAccessor.FieldGetter asmFieldGetter;

    /** 列索引(从0开始) */
    private Integer columnIndex;

    /** 格式化字符串 */
    private String format;

    /** 日期格式 */
    private String dateFormat;

    /** 列宽度 */
    private Short width;

    /** 排序顺序 */
    private Integer order;

    /** 自定义转换器类 */
    private Class<?> converterClass;

    /** 默认值 */
    private String defaultValue;

    /** 是否需要处理器 */
    private Boolean needHandler;

    /** 样式注解 */
    private ExcelStyle style;

    /** 公式表达式 */
    private String formula;

    public WriteHeaderProperty() {}

    public WriteHeaderProperty(String name, Field field) {
      this.name = name;
      this.field = field;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public Field getField() {
      return field;
    }

    public void setField(Field field) {
      this.field = field;
    }

    /**
     * 获取 ASM 字节码生成的字段访问器，用于替代反射以提升取值性能。
     *
     * <p>本方法<b>不做延迟初始化</b>，仅返回已有引用；访问器需由构建元数据的一方 通过 {@link #setAsmFieldGetter} 预先注入。该字段为 {@code
     * transient}， 序列化后不保留，反序列化场景必须重新注入。
     *
     * @return 字段访问器；尚未注入时返回 {@code null}，调用方需自行回退到反射取值
     */
    public ASMFieldAccessor.FieldGetter getAsmFieldGetter() {
      return asmFieldGetter;
    }

    /**
     * 设置ASM字段Getter访问器
     *
     * @param asmFieldGetter ASM FieldGetter
     */
    public void setAsmFieldGetter(ASMFieldAccessor.FieldGetter asmFieldGetter) {
      this.asmFieldGetter = asmFieldGetter;
    }

    public Integer getColumnIndex() {
      return columnIndex;
    }

    public void setColumnIndex(Integer columnIndex) {
      this.columnIndex = columnIndex;
    }

    public String getFormat() {
      return format;
    }

    public void setFormat(String format) {
      this.format = format;
    }

    public String getDateFormat() {
      return dateFormat;
    }

    public void setDateFormat(String dateFormat) {
      this.dateFormat = dateFormat;
    }

    public Short getWidth() {
      return width;
    }

    public void setWidth(Short width) {
      this.width = width;
    }

    public Integer getOrder() {
      return order;
    }

    public void setOrder(Integer order) {
      this.order = order;
    }

    public Class<?> getConverterClass() {
      return converterClass;
    }

    public void setConverterClass(Class<?> converterClass) {
      this.converterClass = converterClass;
    }

    public String getDefaultValue() {
      return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
      this.defaultValue = defaultValue;
    }

    public Boolean getNeedHandler() {
      return needHandler;
    }

    public void setNeedHandler(Boolean needHandler) {
      this.needHandler = needHandler;
    }

    public ExcelStyle getStyle() {
      return style;
    }

    public void setStyle(ExcelStyle style) {
      this.style = style;
    }

    public String getFormula() {
      return formula;
    }

    public void setFormula(String formula) {
      this.formula = formula;
    }
  }
}
