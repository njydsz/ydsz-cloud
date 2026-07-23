package com.njydsz.common.excel.core.model;

/**
 * Sheet数据封装类
 *
 * <p>封装一个Sheet所需的完整数据信息</p>
 *
 * @author ydsz-team
 * @email ydsz-dev@njydsz.com
 * @version 1.0.0
 */
public class SheetData {
    private String sheetName;
    private Class<?> clazz;
    private Object data;
    private Integer headRowNumber;

    public SheetData() {
    }

    public SheetData(String sheetName, Class<?> clazz, Object data) {
        this.sheetName = sheetName;
        this.clazz = clazz;
        this.data = data;
    }

    public String getSheetName() {
        return sheetName;
    }

    public void setSheetName(String sheetName) {
        this.sheetName = sheetName;
    }

    public Class<?> getClazz() {
        return clazz;
    }

    public void setClazz(Class<?> clazz) {
        this.clazz = clazz;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public Integer getHeadRowNumber() {
        return headRowNumber;
    }

    public void setHeadRowNumber(Integer headRowNumber) {
        this.headRowNumber = headRowNumber;
    }
}
