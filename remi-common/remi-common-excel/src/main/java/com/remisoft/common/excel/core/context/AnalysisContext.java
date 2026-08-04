package com.remisoft.common.excel.core.context;

import com.remisoft.common.excel.core.ExcelReader;
import com.remisoft.common.excel.core.metadata.ReadMetadata;
import com.remisoft.common.excel.core.metadata.WriteMetadata;

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
 * @author remi-team
 * @since 1.0.0
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

    /**
     * 将当前行号前进一行。
     *
     * <p>由解析流程在每完成一行回调后调用，是 {@link #getCurrentRow()} 的唯一推进方式。
     * 仅自增不做上界校验，与 {@code totalRows} 无联动，越界与否由调用方保证。
     *
     * <p><b>线程安全性</b>：非原子操作，本上下文假定单线程串行解析，
     * 多线程共享同一实例会丢失计数。
     */
    public void incrementRow() {
        this.currentRow++;
    }
}