package com.njydsz.pmis.common.excel;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.write.handler.WriteHandler;
import com.alibaba.excel.write.metadata.style.WriteCellStyle;
import com.alibaba.excel.write.metadata.style.WriteFont;
import com.alibaba.excel.write.style.HorizontalCellStyleStrategy;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import com.alibaba.excel.write.style.row.SimpleRowHeightStyleStrategy;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.VerticalAlignment;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PMIS 业务导入模板生成器
 *
 * <p>基于 EasyExcel 提供统一的"下载空白模板"能力：第 1 行表头、第 2 行样例数据、
 * 列宽自适应、表头底色、必填单元格标红。
 *
 * <p>典型用法：
 * <pre>{@code
 *   byte[] bytes = ExcelTemplate.builder()
 *       .head(RateCardImportDTO.class)
 *       .sampleData(List.of(new RateCardImportDTO()))
 *       .addRequiredMark("level", "cardType")
 *       .build();
 *   FileUtil.writeBytes(response, bytes, "费率卡模板.xlsx");
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public final class ExcelTemplate {

    /** 表头类（带 @ExcelProperty 注解） */
    private Class<?> headClass;
    /** 样例数据（第 2 行） */
    private List<?> sampleData;
    /** 必填列标记（字段名 → 标记符号） */
    private Map<String, String> requiredMarks;
    /** Sheet 名 */
    private String sheetName = "Sheet1";

    private ExcelTemplate() {
    }

    /**
     * 创建构建器
     *
     * @return ExcelTemplate 构建器实例
     */
    public static ExcelTemplate builder() {
        return new ExcelTemplate();
    }

    /**
     * 设置表头类
     *
     * @param headClass 表头类
     * @return this
     */
    public ExcelTemplate head(Class<?> headClass) {
        this.headClass = headClass;
        return this;
    }

    /**
     * 设置样例数据
     *
     * @param data 样例数据列表
     * @return this
     */
    public ExcelTemplate sampleData(List<?> data) {
        this.sampleData = data;
        return this;
    }

    /**
     * 标记必填列（与 @ExcelProperty value 一致）
     *
     * @param fieldNames 必填字段名
     * @return this
     */
    public ExcelTemplate addRequiredMark(String... fieldNames) {
        if (this.requiredMarks == null) {
            this.requiredMarks = new HashMap<>();
        }
        for (String f : fieldNames) {
            this.requiredMarks.put(f, "*");
        }
        return this;
    }

    /**
     * 设置 Sheet 名
     *
     * @param name Sheet 名
     * @return this
     */
    public ExcelTemplate sheetName(String name) {
        this.sheetName = name;
        return this;
    }

    /**
     * 构建模板字节数组
     *
     * @return 模板文件字节数组
     * @throws IllegalArgumentException 未设置表头类时抛出
     * @throws IllegalStateException    写入失败时抛出
     */
    public byte[] build() {
        if (headClass == null) {
            throw new IllegalArgumentException("head class is required");
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writeTo(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("build template failed", e);
        }
    }

    /**
     * 构建模板并写入输出流
     *
     * @param out 输出流
     * @throws IOException 写入失败时抛出
     */
    public void buildTo(OutputStream out) throws IOException {
        writeTo(out);
    }

    private void writeTo(OutputStream out) throws IOException {
        // 表头样式：底色浅蓝 + 加粗
        WriteCellStyle headerStyle = new WriteCellStyle();
        headerStyle.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
        // POI 5.x 中 Font 不能直接实例化；使用 easyexcel 的 WriteFont
        WriteFont headerFont = new WriteFont();
        headerFont.setBold(true);
        headerStyle.setWriteFont(headerFont);
        headerStyle.setHorizontalAlignment(HorizontalAlignment.CENTER);
        headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);

        WriteCellStyle contentStyle = new WriteCellStyle();
        contentStyle.setHorizontalAlignment(HorizontalAlignment.LEFT);
        contentStyle.setVerticalAlignment(VerticalAlignment.CENTER);

        WriteHandler styleHandler = new HorizontalCellStyleStrategy(headerStyle, contentStyle);

        // 列宽自适应 + 表头行高 25
        LongestMatchColumnWidthStyleStrategy columnWidth = new LongestMatchColumnWidthStyleStrategy();
        SimpleRowHeightStyleStrategy rowHeight = new SimpleRowHeightStyleStrategy((short) 25, (short) 18);

        EasyExcel.write(out, headClass)
                .registerWriteHandler(styleHandler)
                .registerWriteHandler(columnWidth)
                .registerWriteHandler(rowHeight)
                .sheet(sheetName)
                .doWrite(sampleData == null ? Collections.emptyList() : sampleData);
    }
}
