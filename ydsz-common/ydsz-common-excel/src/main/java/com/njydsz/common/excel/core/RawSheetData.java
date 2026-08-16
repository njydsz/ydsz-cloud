package com.njydsz.common.excel.core;

import java.util.List;

/**
 * 无类型 Sheet 数据持有者 — 用于无 VO 映射的全 Sheet 读取场景。
 *
 * <p>封装单张 Sheet 的名称、表头和数据行（全部为字符串），
 * 适用于文档解析、数据校验等不需要类型映射的场景。</p>
 *
 * @param sheetName Sheet 名称
 * @param headers   表头列表（无表头时为空列表）
 * @param rows      数据行（每行为字段值字符串列表）
 * @author ydsz-team
 * @since 1.0.0
 */
public record RawSheetData(String sheetName, List<String> headers, List<List<String>> rows) {

    /**
     * 判断当前 Sheet 是否包含数据。
     *
     * @return 有数据行时返回 {@code true}
     */
    public boolean hasData() {
        return rows != null && !rows.isEmpty();
    }

    /**
     * 获取列数（以表头列数为准，无表头时以首行数据列数为准）。
     *
     * @return 列数
     */
    public int columnCount() {
        if (headers != null && !headers.isEmpty()) {
            return headers.size();
        }
        if (rows != null && !rows.isEmpty()) {
            return rows.get(0).size();
        }
        return 0;
    }
}
