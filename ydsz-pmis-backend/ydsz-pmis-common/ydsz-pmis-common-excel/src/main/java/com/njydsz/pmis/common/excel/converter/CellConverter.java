package com.njydsz.pmis.common.excel.converter;

import org.apache.poi.ss.usermodel.Cell;

public interface CellConverter<T> {

    T convert(Cell cell);
}
