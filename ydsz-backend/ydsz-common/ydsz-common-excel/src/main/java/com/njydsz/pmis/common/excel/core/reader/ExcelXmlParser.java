package com.njydsz.common.excel.core.reader;

/**
 * ExcelXmlParser 类
 *
 * @author ydsz-team
 * @email pmis-dev@njydsz.com
 * @version 1.0.0
 */
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ExcelXmlParser {

    private static final byte[] ROW_START = "<row".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ROW_END = "</row>".getBytes(StandardCharsets.UTF_8);
    private static final byte[] CELL_START = "<c".getBytes(StandardCharsets.UTF_8);
    private static final byte[] VALUE_START = "<v>".getBytes(StandardCharsets.UTF_8);
    private static final byte[] VALUE_END = "</v>".getBytes(StandardCharsets.UTF_8);
    private static final byte[] IS_TAG = "<is>".getBytes(StandardCharsets.UTF_8);
    private static final byte[] T_TAG = "<t>".getBytes(StandardCharsets.UTF_8);
    private static final byte[] T_CLOSE = "</t>".getBytes(StandardCharsets.UTF_8);
    private static final byte[] FORMULA_START = "<f>".getBytes(StandardCharsets.UTF_8);
    private static final byte[] FORMULA_END = "</f>".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ATTR_R = " r=\"".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ATTR_T = " t=\"".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ATTR_S = " s=\"".getBytes(StandardCharsets.UTF_8);

    private int pos;
    private int limit;
    private int currentRow;
    private int currentCol;
    private String cellRef;
    private String cellType;
    private int cellStyle;

    private RowHandler rowHandler;
    private CellHandler cellHandler;

    public interface RowHandler {
        void onRowStart(int rowNum);
        void onRowEnd(int rowNum);
    }

    public interface CellHandler {
        void onCellStart(int row, int col, String ref, String type, int style);
        void onCellValue(int row, int col, String value);
        void onCellEnd(int row, int col);
    }

    public ExcelXmlParser(int bufferSize) {
    }

    public void parse(byte[] data, RowHandler rowHandler, CellHandler cellHandler) {
        this.rowHandler = rowHandler;
        this.cellHandler = cellHandler;
        this.pos = 0;
        this.limit = data.length;
        this.currentRow = -1;
        this.currentCol = -1;

        while (pos < limit) {
            if (data[pos] == '<') {
                if (matchTag(data, ROW_START)) {
                    parseRowTag(data);
                } else if (matchTag(data, CELL_START)) {
                    parseCellTag(data);
                } else if (matchTag(data, VALUE_START)) {
                    parseValueTag(data);
                } else {
                    skipTag(data);
                }
            } else {
                pos++;
            }
        }
    }

    private boolean matchTag(byte[] data, byte[] tag) {
        if (pos + tag.length > limit) {
            return false;
        }
        for (int i = 0; i < tag.length; i++) {
            if (data[pos + i] != tag[i]) {
                return false;
            }
        }
        return true;
    }

    private void parseRowTag(byte[] data) {
        int tagEnd = findChar(data, '>', pos);
        if (tagEnd == -1) {
            skipTag(data);
            return;
        }

        int rowNum = parseRowNumber(data, pos);
        if (rowNum > 0) {
            currentRow = rowNum;
            if (rowHandler != null) {
                rowHandler.onRowStart(currentRow);
            }
        }

        pos = tagEnd + 1;

        while (pos < limit && data[pos] != '<') {
            if (data[pos] == '<' && matchTag(data, ROW_END)) {
                if (rowHandler != null && currentRow > 0) {
                    rowHandler.onRowEnd(currentRow);
                }
                pos += ROW_END.length;
                currentRow = -1;
                currentCol = -1;
                return;
            }
            pos++;
        }
    }

    private int parseRowNumber(byte[] data, int start) {
        int pos = start + 4;
        while (pos < limit && data[pos] != 'r') {
            pos++;
        }
        if (pos >= limit || data[pos] != 'r') {
            return -1;
        }

        int eqPos = pos + 1;
        while (eqPos < limit && data[eqPos] != '=' && data[eqPos] != '"') {
            eqPos++;
        }
        while (eqPos < limit && data[eqPos] != '"') {
            eqPos++;
        }
        int valueStart = eqPos + 1;
        int valueEnd = valueStart;
        while (valueEnd < limit && data[valueEnd] != '"') {
            valueEnd++;
        }

        try {
            String numStr = new String(data, valueStart, valueEnd - valueStart, StandardCharsets.UTF_8);
            return Integer.parseInt(numStr);
        } catch (Exception e) {
            return -1;
        }
    }

    private void parseCellTag(byte[] data) {
        int tagEnd = findChar(data, '>', pos);
        if (tagEnd == -1) {
            skipTag(data);
            return;
        }

        cellRef = parseAttribute(data, ATTR_R);
        currentCol = parseCellRef(cellRef);

        cellType = parseAttribute(data, ATTR_T);
        if (cellType == null) {
            cellType = "";
        }

        String styleStr = parseAttribute(data, ATTR_S);
        cellStyle = 0;
        if (styleStr != null && !styleStr.isEmpty()) {
            try {
                cellStyle = Integer.parseInt(styleStr);
            } catch (NumberFormatException e) {
            }
        }

        if (cellHandler != null && currentRow > 0 && currentCol >= 0) {
            cellHandler.onCellStart(currentRow, currentCol, cellRef, cellType, cellStyle);
        }

        if (cellType.equals("inlineStr") || cellType.equals("str")) {
            int isPos = findBytes(data, IS_TAG, pos, tagEnd);
            if (isPos == -1) {
                isPos = findBytes(data, T_TAG, pos, tagEnd);
            }
            if (isPos != -1) {
                int tStart = isPos + IS_TAG.length;
                int tEnd = findBytes(data, T_CLOSE, tStart, tagEnd);
                if (tEnd == -1) {
                    tEnd = findBytes(data, "</t>", tStart, tagEnd);
                }
                if (tEnd != -1) {
                    String value = new String(data, tStart, tEnd - tStart, StandardCharsets.UTF_8);
                    if (cellHandler != null && currentRow > 0 && currentCol >= 0) {
                        cellHandler.onCellValue(currentRow, currentCol, value);
                    }
                }
            }
        } else if (cellType.equals("e") || cellType.equals("b") || cellType.equals("n") ||
                   cellType.equals("s") || cellType.equals("str") || cellType.isEmpty()) {
            int vStart = findBytes(data, VALUE_START, pos, tagEnd);
            if (vStart == -1) {
                vStart = findBytes(data, FORMULA_START, pos, tagEnd);
            }
            if (vStart != -1) {
                int valueStart = vStart + VALUE_START.length;
                int valueEnd = findBytes(data, VALUE_END, valueStart, tagEnd);
                int formulaEnd = findBytes(data, FORMULA_END, valueStart, tagEnd);
                if (valueEnd == -1 && formulaEnd != -1) {
                    valueEnd = formulaEnd;
                }
                if (valueEnd != -1) {
                    String value = new String(data, valueStart, valueEnd - valueStart, StandardCharsets.UTF_8);
                    if (cellHandler != null && currentRow > 0 && currentCol >= 0) {
                        cellHandler.onCellValue(currentRow, currentCol, value);
                    }
                }
            }
        }

        pos = tagEnd + 1;
    }

    private void parseValueTag(byte[] data) {
        int valueStart = pos + VALUE_START.length;
        int valueEnd = findBytes(data, VALUE_END, valueStart, limit);
        if (valueEnd == -1) {
            valueEnd = findBytes(data, "</v>", valueStart, limit);
        }
        if (valueEnd != -1) {
            String value = new String(data, valueStart, valueEnd - valueStart, StandardCharsets.UTF_8);
            if (cellHandler != null && currentRow > 0 && currentCol >= 0) {
                cellHandler.onCellValue(currentRow, currentCol, value);
            }
        }
        pos = valueEnd > 0 ? valueEnd + VALUE_END.length : pos + 1;
    }

    private String parseAttribute(byte[] data, byte[] attrName) {
        int attrPos = findBytes(data, attrName, pos, limit);
        if (attrPos == -1) {
            return null;
        }

        int valueStart = attrPos + attrName.length;
        int valueEnd = valueStart;
        while (valueEnd < limit && data[valueEnd] != '"') {
            valueEnd++;
        }

        if (valueEnd > valueStart) {
            return new String(data, valueStart, valueEnd - valueStart, StandardCharsets.UTF_8);
        }
        return "";
    }

    private int parseCellRef(String ref) {
        if (ref == null || ref.isEmpty()) {
            return -1;
        }

        int col = 0;
        for (int i = 0; i < ref.length(); i++) {
            char c = ref.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                col = col * 26 + (c - 'A' + 1);
            } else if (c >= '0' && c <= '9') {
                break;
            }
        }
        return col - 1;
    }

    private int findChar(byte[] data, char c, int start) {
        for (int i = start; i < limit && i < data.length; i++) {
            if (data[i] == c) {
                return i;
            }
        }
        return -1;
    }

    private int findBytes(byte[] data, byte[] target, int start, int end) {
        if (end > data.length) {
            end = data.length;
        }
        outer:
        for (int i = start; i <= end - target.length; i++) {
            for (int j = 0; j < target.length; j++) {
                if (data[i + j] != target[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private int findBytes(byte[] data, String target, int start, int end) {
        byte[] targetBytes = target.getBytes(StandardCharsets.UTF_8);
        return findBytes(data, targetBytes, start, end);
    }

    private void skipTag(byte[] data) {
        int depth = 1;
        while (pos < limit && depth > 0) {
            if (data[pos] == '<') {
                if (pos + 1 < limit) {
                    if (data[pos + 1] == '/') {
                        depth--;
                        if (depth == 0) {
                            int endTag = findChar(data, '>', pos);
                            if (endTag != -1) {
                                pos = endTag + 1;
                            } else {
                                pos = limit;
                            }
                            return;
                        }
                    } else if (data[pos + 1] == '!') {
                    } else if (data[pos + 1] == '?') {
                    } else {
                        boolean isEndTag = false;
                        for (int i = 1; i < 5; i++) {
                            if (pos + i >= limit) break;
                            if (data[pos + i] == '/') {
                                isEndTag = true;
                                break;
                            }
                            if (data[pos + i] == ' ' || data[pos + i] == '>') {
                                break;
                            }
                        }
                        if (!isEndTag) {
                            depth++;
                        }
                    }
                }
            }
            pos++;
            if (pos >= limit) {
                break;
            }
        }
        int endTag = findChar(data, '>', pos);
        if (endTag != -1) {
            pos = endTag + 1;
        }
    }

    public static List<ParsedCell> parseCells(byte[] sheetData, ChunkedSSTTable sstTable) {
        List<ParsedCell> cells = new ArrayList<>();
        ExcelXmlParser parser = new ExcelXmlParser(8192);

        parser.parse(sheetData, null, new CellHandler() {
            @Override
            public void onCellStart(int row, int col, String ref, String type, int style) {
            }

            @Override
            public void onCellValue(int row, int col, String value) {
                ParsedCell cell = new ParsedCell();
                cell.row = row;
                cell.col = col;
                cell.value = value;
                cells.add(cell);
            }

            @Override
            public void onCellEnd(int row, int col) {
            }
        });

        return cells;
    }

    public static class ParsedCell {
        public int row;
        public int col;
        public String value;
        public String type;

        @Override
        public String toString() {
            return "ParsedCell{row=" + row + ", col=" + col + ", value='" + value + "', type='" + type + "'}";
        }
    }
}
