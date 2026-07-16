package com.njydsz.common.excel.converter;

/**
 * 单元格值转换上下文
 *
 * <p>在转换器链中传递的上下文信息，包含当前行列信息、
 * 日期格式、自动修剪、严格数字转换、1904日期窗口等配置。</p>
 *
 * @author ydsz-team
 * @email pmis-dev@njydsz.com
 * @version 1.0.0
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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final ConvertContext context = new ConvertContext();

        public Builder rowIndex(int rowIndex) {
            context.rowIndex = rowIndex;
            return this;
        }

        public Builder columnName(String columnName) {
            context.columnName = columnName;
            return this;
        }

        public Builder dateFormat(String dateFormat) {
            context.dateFormat = dateFormat;
            return this;
        }

        public Builder automaticTrim(boolean automaticTrim) {
            context.automaticTrim = automaticTrim;
            return this;
        }

        public Builder strictNumberConversion(boolean strictNumberConversion) {
            context.strictNumberConversion = strictNumberConversion;
            return this;
        }

        public Builder use1904Windowing(boolean use1904Windowing) {
            context.use1904Windowing = use1904Windowing;
            return this;
        }

        public ConvertContext build() {
            return context;
        }
    }
}
