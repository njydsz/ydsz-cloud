package com.njydsz.common.excel.core.reader;

import java.time.LocalDateTime;
import java.util.Calendar;
import java.util.Date;
import org.apache.poi.ss.formula.FormulaParseException;
import org.apache.poi.ss.util.CellAddress;
import org.apache.poi.ss.util.CellRangeAddress;
/**
 * 轻量级单元格实现 - 用于 SAX 模式解析
 *
 * <p>在 SAX 模式下不需要完整的 POI Cell 对象，但 TypeConvertStrategy
 * 的 convert 方法接收 Cell 参数。此类提供轻量级的实现，
 * 只包含类型转换所需的最小功能。</p>
 *
 * <h3>设计说明</h3>
 * <p>此类仅用于 SAX 模式解析，不依赖 POI 的内部实现。
 * 只实现了类型转换相关的方法，其他方法返回 null 或默认值。</p>
 *
 * @author ydsz-team
 * @email ydsz-dev@ydszsoft.com
 * @version 1.0.0
 * @since 1.0.0
 */
public final class SimpleCell implements Cell {

    /** 单元格值 */
    private final String value;

    /** 单元格类型 */
    private final CellType cellType;

    /**
     * 创建轻量级单元格
     *
     * @param value 单元格值
     * @param cellType 单元格类型
     */
    public SimpleCell(String value, CellType cellType) {
        this.value = value;
        this.cellType = cellType;
    }

    @Override
    public CellType getCellType() {
        return cellType;
    }

    @Override
    public CellType getCachedFormulaResultType() {
        return cellType;
    }

    @Override
    public String getStringCellValue() {
        return value;
    }

    @Override
    public RichTextString getRichStringCellValue() {
        return null;
    }

    @Override
    public double getNumericCellValue() {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    @Override
    public boolean getBooleanCellValue() {
        return Boolean.parseBoolean(value);
    }

    @Override
    public Date getDateCellValue() {
        return null;
    }

    @Override
    public LocalDateTime getLocalDateTimeCellValue() {
        return null;
    }

    @Override
    public void setCellType(CellType cellType) {
    }

    @Override
    public void setCellValue(String value) {
    }

    @Override
    public void setCellValue(double value) {
    }

    @Override
    public void setCellValue(RichTextString value) {
    }

    @Override
    public void setCellValue(Date value) {
    }

    @Override
    public void setCellValue(LocalDateTime value) {
    }

    @Override
    public void setCellFormula(String formula) throws IllegalStateException, FormulaParseException {
    }

    @Override
    public String getCellFormula() {
        return null;
    }

    @Override
    public void setBlank() {
    }

    @Override
    public byte getErrorCellValue() {
        return 0;
    }

    @Override
    public void setCellErrorValue(byte error) {
    }

    @Override
    public int getColumnIndex() {
        return 0;
    }

    @Override
    public int getRowIndex() {
        return 0;
    }

    @Override
    public Sheet getSheet() {
        return null;
    }

    @Override
    public Row getRow() {
        return null;
    }

    @Override
    public CellStyle getCellStyle() {
        return null;
    }

    @Override
    public void setCellStyle(CellStyle style) {
    }

    @Override
    public CellAddress getAddress() {
        return null;
    }

    @Override
    public void setAsActiveCell() {
    }

    @Override
    public Comment getCellComment() {
        return null;
    }

    @Override
    public void setCellComment(Comment comment) {
    }

    @Override
    public void removeCellComment() {
    }

    @Override
    public Hyperlink getHyperlink() {
        return null;
    }

    @Override
    public void setHyperlink(Hyperlink link) {
    }

    @Override
    public void removeHyperlink() {
    }

    @Override
    public CellRangeAddress getArrayFormulaRange() {
        return null;
    }

    @Override
    public boolean isPartOfArrayFormulaGroup() {
        return false;
    }

    @Override
    public void removeFormula() {
    }

    @Override
    public void setCellValue(boolean value) {
    }

    @Override
    public void setCellValue(Calendar value) {
    }
}
