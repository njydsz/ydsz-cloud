package com.njydsz.pmis.common.doc.excel;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.write.builder.ExcelWriterBuilder;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * Excel 模板构建器。
 *
 * <p>封装 EasyExcel 的模板生成逻辑，支持表头、样例数据、必填标记等功能。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class ExcelTemplate {

    /** 表头类 */
    private Class<?> head;
    /** 样例数据 */
    private List<?> sampleData;
    /** Sheet 名称 */
    private String sheetName = "Sheet1";
    /** 必填字段标记 */
    private String[] requiredFields;

    private ExcelTemplate() {
    }

    /**
     * 创建构建器。
     *
     * @return 构建器实例
     */
    public static ExcelTemplate builder() {
        return new ExcelTemplate();
    }

    /**
     * 设置表头类。
     *
     * @param head 表头类
     * @return this
     */
    public ExcelTemplate head(Class<?> head) {
        this.head = head;
        return this;
    }

    /**
     * 设置样例数据。
     *
     * @param sampleData 样例数据列表
     * @param <T>        数据类型
     * @return this
     */
    @SuppressWarnings("unchecked")
    public <T> ExcelTemplate sampleData(List<T> sampleData) {
        this.sampleData = sampleData;
        return this;
    }

    /**
     * 设置 Sheet 名称。
     *
     * @param sheetName Sheet 名称
     * @return this
     */
    public ExcelTemplate sheetName(String sheetName) {
        this.sheetName = sheetName;
        return this;
    }

    /**
     * 设置必填字段标记（在表头中标注 * 号）。
     *
     * @param fields 必填字段名数组
     * @return this
     */
    public ExcelTemplate addRequiredMark(String... fields) {
        this.requiredFields = fields;
        return this;
    }

    /**
     * 构建 Excel 文件字节数组。
     *
     * @return Excel 文件字节数组
     */
    public byte[] build() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ExcelWriterBuilder writerBuilder = EasyExcel.write(out, head);
        if (sampleData != null && !sampleData.isEmpty()) {
            writerBuilder.sheet(sheetName).doWrite(sampleData);
        } else {
            writerBuilder.sheet(sheetName).doWrite(List.of());
        }
        return out.toByteArray();
    }
}
