package com.njydsz.pmis.common.docs.domain;

import java.util.List;

import lombok.Builder;
import lombok.Data;

/**
 * 文档表格模型
 * <p>
 * 从文档中提取的表格结构化数据。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 1.0.0
 * @since 1.3.0
 */
@Data
@Builder
public class DocumentTable {

    /** 表格标题（如有） */
    private String caption;

    /** 页码 */
    private Integer pageNumber;

    /** 行数 */
    private int rowCount;

    /** 列数 */
    private int colCount;

    /** 表格数据（行优先） */
    private List<List<String>> rows;

    /** 是否包含合并单元格 */
    private boolean hasMergedCells;
}
