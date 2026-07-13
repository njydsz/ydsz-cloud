package com.njydsz.pmis.common.excel.core.context;

/**
 * AnalysisContext 类
 *
 * @author ydsz-pmis-team
 * @email pmis-dev@njydsz.com
 * @version 1.0.0
 */
import com.njydsz.pmis.common.excel.core.ExcelReader;
import com.njydsz.pmis.common.excel.core.metadata.ReadMetadata;
import com.njydsz.pmis.common.excel.core.metadata.WriteMetadata;

/**
 * 分析上下文 - 读写过程中状态信息的载体
 *
 * <p>贯穿整个Excel读写流程,用于在各组件间传递状态信息。
 * 主要在监听器回调时使用,让调用方了解当前的读取进度。</p>
 *
 * <h3>用途</h3>
 * <ul>
 *   <li>监听器回调 - 传递读取上下文信息</li>
 *   <li>进度追踪 - currentRow、totalRows</li>
 *   <li>性能监控 - readTime、writeTime</li>
 *   <li>元数据访问 - 提供ReadMetadata/WriteMetadata的访问</li>
 * </ul>
 *
 * @see ReadListener
 * @see ExcelReader
 */
public class AnalysisContext {

    /** 读取元数据(读取模式下使用) */
    private ReadMetadata readMetadata;

    /** 写入元数据(写入模式下使用) */
    private WriteMetadata writeMetadata;

    /** 当前处理的行号(从0开始) */
    private int currentRow;

    /** 当前处理的列索引(从0开始) */
    private int currentColumn;

    /** 当前处理的Sheet索引 */
    private int currentSheetIndex;

    /** 当前处理的Sheet名称 */
    private String currentSheetName;

    /** 读取耗时(毫秒) */
    private long readTime;

    /** 写入耗时(毫秒) */
    private long writeTime;

    /** 总行数 */
    private int totalRows;

    /** 总Sheet数 */
    private int totalSheets;

    public AnalysisContext() {
    }

    public AnalysisContext(ReadMetadata readMetadata) {
        this.readMetadata = readMetadata;
        this.currentRow = 0;
        this.currentSheetIndex = 0;
    }

    public AnalysisContext(WriteMetadata writeMetadata) {
        this.writeMetadata = writeMetadata;
        this.currentRow = 0;
        this.currentSheetIndex = 0;
    }

    public ReadMetadata getReadMetadata() {
        return readMetadata;
    }

    public void setReadMetadata(ReadMetadata readMetadata) {
        this.readMetadata = readMetadata;
    }

    public WriteMetadata getWriteMetadata() {
        return writeMetadata;
    }

    public void setWriteMetadata(WriteMetadata writeMetadata) {
        this.writeMetadata = writeMetadata;
    }

    public int getCurrentRow() {
        return currentRow;
    }

    public void setCurrentRow(int currentRow) {
        this.currentRow = currentRow;
    }

    public int getCurrentColumn() {
        return currentColumn;
    }

    public void setCurrentColumn(int currentColumn) {
        this.currentColumn = currentColumn;
    }

    public int getCurrentSheetIndex() {
        return currentSheetIndex;
    }

    public void setCurrentSheetIndex(int currentSheetIndex) {
        this.currentSheetIndex = currentSheetIndex;
    }

    public String getCurrentSheetName() {
        return currentSheetName;
    }

    public void setCurrentSheetName(String currentSheetName) {
        this.currentSheetName = currentSheetName;
    }

    public long getReadTime() {
        return readTime;
    }

    public void setReadTime(long readTime) {
        this.readTime = readTime;
    }

    public long getWriteTime() {
        return writeTime;
    }

    public void setWriteTime(long writeTime) {
        this.writeTime = writeTime;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(int totalRows) {
        this.totalRows = totalRows;
    }

    public int getTotalSheets() {
        return totalSheets;
    }

    public void setTotalSheets(int totalSheets) {
        this.totalSheets = totalSheets;
    }

    public void incrementRow() {
        this.currentRow++;
    }
}