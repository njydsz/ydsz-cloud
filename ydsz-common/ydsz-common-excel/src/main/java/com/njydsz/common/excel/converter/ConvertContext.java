package com.njydsz.common.excel.converter;

/**
 * 单元格值转换上下文
 *
 * <p>在转换器链中传递的上下文信息，包含当前行列信息、 日期格式、自动修剪、严格数字转换、1904日期窗口等配置。
 *
 * @author ydsz-team

 * @version 1.0.0
 * @since 1.0.0
 */
public class ConvertContext {

  private int rowIndex;
  private String columnName;
  private String dateFormat;
  private boolean automaticTrim;
  private boolean strictNumberConversion;
  private boolean use1904Windowing;

  public int getRowIndex() {
    return rowIndex;
  }

  public void setRowIndex(int rowIndex) {
    this.rowIndex = rowIndex;
  }

  public String getColumnName() {
    return columnName;
  }

  public void setColumnName(String columnName) {
    this.columnName = columnName;
  }

  public String getDateFormat() {
    return dateFormat;
  }

  public void setDateFormat(String dateFormat) {
    this.dateFormat = dateFormat;
  }

  public boolean isAutomaticTrim() {
    return automaticTrim;
  }

  public void setAutomaticTrim(boolean automaticTrim) {
    this.automaticTrim = automaticTrim;
  }

  public boolean isStrictNumberConversion() {
    return strictNumberConversion;
  }

  public void setStrictNumberConversion(boolean strictNumberConversion) {
    this.strictNumberConversion = strictNumberConversion;
  }

  public boolean isUse1904Windowing() {
    return use1904Windowing;
  }

  public void setUse1904Windowing(boolean use1904Windowing) {
    this.use1904Windowing = use1904Windowing;
  }

  /**
   * 创建转换上下文构建器。
   *
   * @return 全新的构建器实例
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * {@link ConvertContext} 构建器。
   *
   * <p><b>注意</b>：构建器内部复用同一个 {@link ConvertContext} 实例， {@link #build()} 返回的正是该实例而非副本。因此一个构建器只应构建一次；
   * 构建后继续调用 setter 会直接改写已返回的对象。
   *
   * @author ydsz-team
   * @since 1.0.0
   */
  public static class Builder {
    private final ConvertContext context = new ConvertContext();

    /**
     * 设置当前单元格所在行号，用于转换失败时定位问题数据。
     *
     * @param rowIndex 行号，由调用方约定从 0 还是 1 开始
     * @return 当前构建器，便于链式调用
     */
    public Builder rowIndex(int rowIndex) {
      context.rowIndex = rowIndex;
      return this;
    }

    /**
     * 设置当前单元格所属列名，用于转换失败时定位问题字段。
     *
     * @param columnName 列名，可为 {@code null}
     * @return 当前构建器，便于链式调用
     */
    public Builder columnName(String columnName) {
      context.columnName = columnName;
      return this;
    }

    /**
     * 设置日期解析/格式化模式。
     *
     * @param dateFormat 日期模式串（如 {@code yyyy-MM-dd}）， 为 {@code null} 时由具体转换器回退到各自的默认模式
     * @return 当前构建器，便于链式调用
     */
    public Builder dateFormat(String dateFormat) {
      context.dateFormat = dateFormat;
      return this;
    }

    /**
     * 设置是否自动去除字符串值的首尾空白。
     *
     * <p>Excel 手工录入数据常带不可见空格，开启后可避免下游精确匹配失败。
     *
     * @param automaticTrim {@code true} 表示自动 trim
     * @return 当前构建器，便于链式调用
     */
    public Builder automaticTrim(boolean automaticTrim) {
      context.automaticTrim = automaticTrim;
      return this;
    }

    /**
     * 设置数字转换是否采用严格模式。
     *
     * <p>严格模式下无法解析的数值将转换失败并上报错误； 宽松模式下则降级为 {@code null} 或默认值继续处理，适合容忍脏数据的批量导入。
     *
     * @param strictNumberConversion {@code true} 表示严格模式
     * @return 当前构建器，便于链式调用
     */
    public Builder strictNumberConversion(boolean strictNumberConversion) {
      context.strictNumberConversion = strictNumberConversion;
      return this;
    }

    /**
     * 设置是否采用 1904 日期系统。
     *
     * <p>Mac 版 Excel 早期以 1904-01-01 为纪元，与默认的 1900 系统相差 1462 天。 若源文件工作簿标记了 1904 窗口而此处未开启，所有日期都会整体偏移约
     * 4 年。
     *
     * @param use1904Windowing {@code true} 表示按 1904 纪元解析日期序列号
     * @return 当前构建器，便于链式调用
     */
    public Builder use1904Windowing(boolean use1904Windowing) {
      context.use1904Windowing = use1904Windowing;
      return this;
    }

    /**
     * 返回构建好的上下文。
     *
     * <p>返回的是构建器内部持有的实例本身，非防御性副本，详见类注释的复用限制说明。
     *
     * @return 转换上下文，永不为 {@code null}
     */
    public ConvertContext build() {
      return context;
    }
  }
}
