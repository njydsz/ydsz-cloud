package com.njydsz.common.excel.core.metadata;

import java.io.File;
import java.io.OutputStream;
import java.util.*;

import com.njydsz.common.excel.annotation.ExcelSheet;

/**
 * WriteMetadata 建造者类 - 建造者模式实现
 *
 * <p>用于链式构建 WriteMetadata 对象,提供流畅的API。
 * 支持单个配置和批量配置两种方式。</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 方式1: 链式调用
 * WriteMetadata metadata = WriteMetadataBuilder.create()
 *     .clazz(User.class)
 *     .filePath("output.xlsx")
 *     .sheetName("用户信息")
 *     .headRowNumber(1)
 *     .dateFormat("yyyy-MM-dd")
 *     .automaticTrim(true)
 *     .build();
 *
 * // 方式2: 使用 @ExcelSheet 注解
 * WriteMetadata metadata = WriteMetadataBuilder.create()
 *     .clazz(User.class)
 *     .fromAnnotation(User.class.getAnnotation(ExcelSheet.class))
 *     .build();
 *
 * // 方式3: 复制已有配置并修改
 * WriteMetadata newMetadata = WriteMetadataBuilder.from(existingMetadata)
 *     .sheetName("新Sheet")
 *     .build();
 * }</pre>
 *
 * @see WriteMetadata
 * @author ydsz-team
 * @email pmis-dev@njydsz.com
 * @version 1.0.0
 */
public class WriteMetadataBuilder {

    private final WriteMetadata metadata;

    private WriteMetadataBuilder() {
        this.metadata = new WriteMetadata();
    }

    private WriteMetadataBuilder(WriteMetadata existing) {
        this.metadata = copyFrom(existing);
    }

    /**
     * 创建新的建造者实例
     *
     * @return 新的建造者
     */
    public static WriteMetadataBuilder create() {
        return new WriteMetadataBuilder();
    }

    /**
     * 从已有 WriteMetadata 创建建造者
     *
     * @param existing 已有的 WriteMetadata
     * @return 基于已有配置的建造者
     */
    public static WriteMetadataBuilder from(WriteMetadata existing) {
        return new WriteMetadataBuilder(existing);
    }

    /**
     * 复制已有配置
     *
     * @param existing 源配置
     * @return 复制后的配置
     */
    private static WriteMetadata copyFrom(WriteMetadata existing) {
        WriteMetadata copy = new WriteMetadata();
        copy.setClazz(existing.getClazz());
        copy.setFilePath(existing.getFilePath());
        copy.setFile(existing.getFile());
        copy.setOutputStream(existing.getOutputStream());
        copy.setSheetName(existing.getSheetName());
        copy.setSheetNo(existing.getSheetNo());
        copy.setHeadRowNumber(existing.getHeadRowNumber());
        copy.setDateFormat(existing.getDateFormat());
        copy.setNumberFormat(existing.getNumberFormat());
        copy.setAutomaticTrim(existing.getAutomaticTrim());
        copy.setPassword(existing.getPassword());
        copy.setExcludeColumnFiledNames(existing.getExcludeColumnFiledNames());
        copy.setIncludeColumnFiledNames(existing.getIncludeColumnFiledNames());
        copy.setDataSize(existing.getDataSize());
        copy.setFreezePaneRow(existing.getFreezePaneRow());
        copy.setFreezePaneCol(existing.getFreezePaneCol());
        copy.setAutoColumnWidth(existing.getAutoColumnWidth());
        copy.setMergedRegions(existing.getMergedRegions());
        return copy;
    }

    // ==================== 配置方法 ====================

    /**
     * 设置数据类型
     *
     * @param clazz Java数据类型
     * @return 当前建造者
     */
    public WriteMetadataBuilder clazz(Class<?> clazz) {
        metadata.setClazz(clazz);
        return this;
    }

    /**
     * 设置文件路径
     *
     * @param filePath 文件路径
     * @return 当前建造者
     */
    public WriteMetadataBuilder filePath(String filePath) {
        metadata.setFilePath(filePath);
        return this;
    }

    /**
     * 设置文件对象
     *
     * @param file 文件对象
     * @return 当前建造者
     */
    public WriteMetadataBuilder file(File file) {
        metadata.setFile(file);
        return this;
    }

    /**
     * 设置输出流
     *
     * @param outputStream 输出流
     * @return 当前建造者
     */
    public WriteMetadataBuilder outputStream(OutputStream outputStream) {
        metadata.setOutputStream(outputStream);
        return this;
    }

    /**
     * 设置Sheet名称
     *
     * @param sheetName Sheet名称
     * @return 当前建造者
     */
    public WriteMetadataBuilder sheetName(String sheetName) {
        metadata.setSheetName(sheetName);
        return this;
    }

    /**
     * 设置Sheet序号
     *
     * @param sheetNo Sheet序号(从0开始)
     * @return 当前建造者
     */
    public WriteMetadataBuilder sheetNo(int sheetNo) {
        metadata.setSheetNo(sheetNo);
        return this;
    }

    /**
     * 设置表头行号
     *
     * @param headRowNumber 表头行号(从0开始)
     * @return 当前建造者
     */
    public WriteMetadataBuilder headRowNumber(int headRowNumber) {
        metadata.setHeadRowNumber(headRowNumber);
        return this;
    }

    /**
     * 设置日期格式
     *
     * @param dateFormat 日期格式字符串
     * @return 当前建造者
     */
    public WriteMetadataBuilder dateFormat(String dateFormat) {
        metadata.setDateFormat(dateFormat);
        return this;
    }

    /**
     * 设置数字格式
     *
     * @param numberFormat 数字格式字符串
     * @return 当前建造者
     */
    public WriteMetadataBuilder numberFormat(String numberFormat) {
        metadata.setNumberFormat(numberFormat);
        return this;
    }

    /**
     * 设置是否自动去空格
     *
     * @param automaticTrim true表示自动去空格
     * @return 当前建造者
     */
    public WriteMetadataBuilder automaticTrim(boolean automaticTrim) {
        metadata.setAutomaticTrim(automaticTrim);
        return this;
    }

    /**
     * 设置Sheet保护密码
     *
     * @param password 保护密码
     * @return 当前建造者
     */
    public WriteMetadataBuilder password(String password) {
        metadata.setPassword(password);
        return this;
    }

    /**
     * 设置排除的字段名
     *
     * @param excludeColumnFiledNames 要排除的字段名集合
     * @return 当前建造者
     */
    public WriteMetadataBuilder excludeColumns(Set<String> excludeColumnFiledNames) {
        metadata.setExcludeColumnFiledNames(excludeColumnFiledNames);
        return this;
    }

    /**
     * 添加排除的字段名
     *
     * @param fieldNames 要排除的字段名
     * @return 当前建造者
     */
    public WriteMetadataBuilder excludeColumns(String... fieldNames) {
        Set<String> set = metadata.getExcludeColumnFiledNames();
        if (set == null) {
            set = new HashSet<>();
            metadata.setExcludeColumnFiledNames(set);
        }
        set.addAll(Arrays.asList(fieldNames));
        return this;
    }

    /**
     * 设置包含的字段名
     *
     * @param includeColumnFiledNames 要包含的字段名集合
     * @return 当前建造者
     */
    public WriteMetadataBuilder includeColumns(Set<String> includeColumnFiledNames) {
        metadata.setIncludeColumnFiledNames(includeColumnFiledNames);
        return this;
    }

    /**
     * 设置包含的字段名
     *
     * @param fieldNames 要包含的字段名
     * @return 当前建造者
     */
    public WriteMetadataBuilder includeColumns(String... fieldNames) {
        Set<String> set = new HashSet<>(Arrays.asList(fieldNames));
        metadata.setIncludeColumnFiledNames(set);
        return this;
    }

    /**
     * 设置数据大小(用于策略选择)
     *
     * @param dataSize 预估数据大小
     * @return 当前建造者
     */
    public WriteMetadataBuilder dataSize(Integer dataSize) {
        metadata.setDataSize(dataSize);
        return this;
    }

    /**
     * 设置冻结窗格
     *
     * @param row 冻结的行数
     * @param col 冻结的列数
     * @return 当前建造者
     */
    public WriteMetadataBuilder freezePane(int row, int col) {
        metadata.setFreezePaneRow(row);
        metadata.setFreezePaneCol(col);
        return this;
    }

    /**
     * 设置自动列宽
     *
     * @param autoColumnWidth true表示自动调整列宽
     * @return 当前建造者
     */
    public WriteMetadataBuilder autoColumnWidth(boolean autoColumnWidth) {
        metadata.setAutoColumnWidth(autoColumnWidth);
        return this;
    }

    /**
     * 添加合并单元格区域
     *
     * @param startRow 起始行
     * @param endRow 结束行
     * @param startCol 起始列
     * @param endCol 结束列
     * @return 当前建造者
     */
    public WriteMetadataBuilder mergedRegion(int startRow, int endRow, int startCol, int endCol) {
        List<int[]> regions = metadata.getMergedRegions();
        if (regions == null) {
            regions = new ArrayList<>();
            metadata.setMergedRegions(regions);
        }
        regions.add(new int[]{startRow, endRow, startCol, endCol});
        return this;
    }

    /**
     * 从注解配置
     *
     * @param annotation @ExcelSheet注解
     * @return 当前建造者
     */
    public WriteMetadataBuilder fromAnnotation(ExcelSheet annotation) {
        if (annotation == null) {
            return this;
        }

        if (!annotation.name().isEmpty()) {
            sheetName(annotation.name());
        }
        headRowNumber(annotation.headRowNumber());
        if (!annotation.dateFormat().isEmpty()) {
            dateFormat(annotation.dateFormat());
        }

        if (annotation.freezePane().row() > 0 || annotation.freezePane().col() > 0) {
            freezePane(annotation.freezePane().row(), annotation.freezePane().col());
        }

        autoColumnWidth(annotation.autoColumnWidth());

        if (annotation.mergedRegions() != null && annotation.mergedRegions().length > 0) {
            for (ExcelSheet.MergedRegion region : annotation.mergedRegions()) {
                mergedRegion(region.startRow(), region.endRow(), region.startCol(), region.endCol());
            }
        }

        return this;
    }

    /**
     * 构建 WriteMetadata 对象
     *
     * <p>在构建前会进行参数校验</p>
     *
     * @return 验证后的 WriteMetadata
     * @throws IllegalStateException 当配置不完整或冲突时抛出
     */
    public WriteMetadata build() {
        validate();
        return metadata;
    }

    /**
     * 验证配置合法性
     *
     * @throws IllegalStateException 当配置不完整或冲突时
     */
    private void validate() {
        if (metadata.getClazz() == null
            && metadata.getFilePath() == null
            && metadata.getFile() == null
            && metadata.getOutputStream() == null) {
            throw new IllegalStateException("必须设置至少一个输出目标: clazz, filePath, file 或 outputStream");
        }

        if (metadata.getExcludeColumnFiledNames() != null
            && metadata.getIncludeColumnFiledNames() != null) {
            throw new IllegalStateException("excludeColumnFiledNames 和 includeColumnFiledNames 不能同时设置");
        }
    }
}