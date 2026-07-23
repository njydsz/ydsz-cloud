package com.njydsz.common.excel.core.context;

/**
 * WriteContext 类
 *
 * @author ydsz-team
 * @email ydsz-dev@njydsz.com
 * @version 1.0.0
 */
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import com.njydsz.common.excel.core.ExcelWriter;
import com.njydsz.common.excel.core.metadata.WriteMetadata;

/**
 * 写入上下文 - 写入过程中状态信息的载体
 *
 * <p>记录当前写入的位置信息和元数据,用于追踪写入进度。
 * 主要在写入处理器回调和内部状态管理时使用。</p>
 *
 * @see ExcelWriter
 * @see WriteHandler
 */
public class WriteContext {

    /** 写入元数据 */
    private WriteMetadata metadata;

    /** 当前写入的工作簿 */
    private Workbook workbook;

    /** 当前写入的Sheet */
    private Sheet sheet;

    /** 当前写入的行号 */
    private int currentRow;

    /** 当前写入的列号 */
    private int currentColumn;

    /** 是否已完成写入 */
    private boolean finished;

    public WriteContext() {
    }

    public WriteContext(WriteMetadata metadata) {
        this.metadata = metadata;
        this.currentRow = metadata.getHeadRowNumber();
    }

    public WriteMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(WriteMetadata metadata) {
        this.metadata = metadata;
    }

    public Workbook getWorkbook() {
        return workbook;
    }

    public void setWorkbook(Workbook workbook) {
        this.workbook = workbook;
    }

    public Sheet getSheet() {
        return sheet;
    }

    public void setSheet(Sheet sheet) {
        this.sheet = sheet;
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

    public boolean isFinished() {
        return finished;
    }

    public void setFinished(boolean finished) {
        this.finished = finished;
    }

    public void incrementRow() {
        this.currentRow++;
    }

    public void incrementColumn() {
        this.currentColumn++;
    }

    public void resetColumn() {
        this.currentColumn = 0;
    }
}