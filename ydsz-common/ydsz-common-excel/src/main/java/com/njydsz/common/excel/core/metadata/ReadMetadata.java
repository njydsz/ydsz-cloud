package com.njydsz.common.excel.core.metadata;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 读取元数据 - 封装Excel读取配置信息
 *
 * <p>包含读取Excel所需的所有配置参数,如文件路径、映射类型、Sheet信息等。
 * 这些信息在读取过程中被持续使用,贯穿整个解析流程。</p>
 *
 * <h3>配置项分类</h3>
 * <ul>
 *   <li>数据源 - filePath、file、inputStream(三选一,优先级递减)</li>
 *   <li>目标Sheet - sheetName、sheetIndex(二选一)</li>
 *   <li>映射配置 - clazz、headRowNumber</li>
 *   <li>格式配置 - dateFormat、numberFormat</li>
 *   <li>行为配置 - readCacheSize、automaticTrim</li>
 * </ul>
 *
 * @see ExcelReader
 * @see WriteMetadata
 * @author ydsz-team
 * @since 1.0.0
 */
public class ReadMetadata {

    /** 映射的目标类类型 */
    private Class<?> clazz;

    /** 文件路径(优先使用) */
    private String filePath;

    /** File对象 */
    private File file;

    /** 输入流 */
    private InputStream inputStream;

    /** Sheet序号(从0开始) */
    private Integer sheetIndex;

    /** Sheet名称 */
    private String sheetName;

    /** 表头行号(从0开始,默认第1行) */
    private Integer headRowNumber;

    /** 读取缓存大小 */
    private Integer readCacheSize;

    /** 是否使用科学计数法 */
    private Boolean useScientificNotation;

    /** 日期格式 */
    private String dateFormat;

    /** 数字格式 */
    private String numberFormat;

    /** Sheet保护密码 */
    private String password;

    /** 强制使用输入流模式 */
    private Boolean mandatoryUseInputStream;

    /** 表头属性列表 */
    private List<ReadHeaderProperty> headList;

    /** 当前已读取的行号(原子计数器) */
    private AtomicInteger currentReadRow;

    /** 要排除的字段名集合(读取时忽略这些字段) */
    private Set<String> excludeColumnFiledNames;

    /** 要包含的字段名集合(只读取这些字段) */
    private Set<String> includeColumnFiledNames;

    /** 是否跳过空行 */
    private Boolean skipEmptyRows;

    /** 是否校验列数 */
    private Boolean checkColumnCount;

    /** 期望的列数 */
    private Integer expectedColumnCount;

    /** 最大读取行数，超过限制时抛出异常 */
    private Integer maxRows = 100000;

    /**
     * 默认构造方法
     *
     * <p>初始化默认值:
     * <ul>
     *   <li>sheetIndex = 0</li>
     *   <li>headRowNumber = 1</li>
     *   <li>readCacheSize = 1024</li>
     *   <li>useScientificNotation = false</li>
     *   <li>mandatoryUseInputStream = false</li>
     * </ul>
     */
    public ReadMetadata() {
        this.sheetIndex = 0;
        this.headRowNumber = 1;
        this.readCacheSize = 1024;
        this.useScientificNotation = false;
        this.mandatoryUseInputStream = false;
        this.headList = new ArrayList<>();
        this.currentReadRow = new AtomicInteger(0);
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

    public InputStream getInputStream() {
        return inputStream;
    }

    public void setInputStream(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    public Integer getSheetIndex() {
        return sheetIndex;
    }

    public void setSheetIndex(Integer sheetIndex) {
        this.sheetIndex = sheetIndex;
    }

    public String getSheetName() {
        return sheetName;
    }

    public void setSheetName(String sheetName) {
        this.sheetName = sheetName;
    }

    public Integer getHeadRowNumber() {
        return headRowNumber;
    }

    public void setHeadRowNumber(Integer headRowNumber) {
        this.headRowNumber = headRowNumber;
    }

    public Integer getReadCacheSize() {
        return readCacheSize;
    }

    public void setReadCacheSize(Integer readCacheSize) {
        this.readCacheSize = readCacheSize;
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

    public Boolean getMandatoryUseInputStream() {
        return mandatoryUseInputStream;
    }

    public void setMandatoryUseInputStream(Boolean mandatoryUseInputStream) {
        this.mandatoryUseInputStream = mandatoryUseInputStream;
    }

    public List<ReadHeaderProperty> getHeadList() {
        return headList;
    }

    public void setHeadList(List<ReadHeaderProperty> headList) {
        this.headList = headList;
    }

    public AtomicInteger getCurrentReadRow() {
        return currentReadRow;
    }

    public void setCurrentReadRow(AtomicInteger currentReadRow) {
        this.currentReadRow = currentReadRow;
    }

    /**
     * 追加一个表头映射项。
     *
     * <p>追加顺序即列匹配顺序。不做重名或列索引冲突校验，重复添加会产生多条映射，
     * 最终以解析器的取用策略为准，调用方需自行保证唯一性。
     *
     * <p><b>前置条件</b>：{@code headList} 由构造方法初始化，但若调用方先用
     * {@link #setHeadList(List)} 传入了 {@code null}，本方法会抛
     * {@link NullPointerException}。
     *
     * @param property 表头映射项，不做非空校验，传 {@code null} 会被原样加入集合
     */
    public void addHeadProperty(ReadHeaderProperty property) {
        this.headList.add(property);
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

    public Boolean getSkipEmptyRows() {
        return skipEmptyRows;
    }

    public void setSkipEmptyRows(Boolean skipEmptyRows) {
        this.skipEmptyRows = skipEmptyRows;
    }

    public Boolean getCheckColumnCount() {
        return checkColumnCount;
    }

    public void setCheckColumnCount(Boolean checkColumnCount) {
        this.checkColumnCount = checkColumnCount;
    }

    public Integer getExpectedColumnCount() {
        return expectedColumnCount;
    }

    public void setExpectedColumnCount(Integer expectedColumnCount) {
        this.expectedColumnCount = expectedColumnCount;
    }

    public Integer getMaxRows() {
        return maxRows;
    }

    public void setMaxRows(Integer maxRows) {
        this.maxRows = maxRows;
    }

    /**
     * 读取表头属性
     *
     * <p>封装Excel列与Java字段的映射信息,
     * 包括列名、对应字段、列索引、日期格式等。</p>
     */
    public static class ReadHeaderProperty {

        /** 表头名称 */
        private String name;

        /** 对应的Java字段 */
        private Field field;

        /** 列索引(从0开始) */
        private Integer columnIndex;

        /** 格式化字符串 */
        private String format;

        /** 日期格式 */
        private String dateFormat;

        /** 自定义转换器类 */
        private Class<?> converterClass;

        public ReadHeaderProperty() {
        }

        public ReadHeaderProperty(String name, Field field) {
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

        public Class<?> getConverterClass() {
            return converterClass;
        }

        public void setConverterClass(Class<?> converterClass) {
            this.converterClass = converterClass;
        }
    }
}