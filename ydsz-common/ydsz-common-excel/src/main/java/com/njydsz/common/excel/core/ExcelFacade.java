package com.njydsz.common.excel.core;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.njydsz.common.excel.core.listener.ReadListener;
import com.njydsz.common.excel.core.metadata.ReadMetadata;
import com.njydsz.common.excel.core.metadata.WriteMetadata;
import com.njydsz.common.excel.core.model.SheetData;

/**
 * Excel 门面类 — 整个框架的统一入口。
 *
 * <p>封装读取 / 写入 / 模板填充 / 多 Sheet / Web 下载等全部能力。
 * 参照阿里巴巴 EasyExcel 的设计理念，注重性能优化和低内存占用。</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 读取
 * ExcelFacade.read("demo.xlsx", User.class)
 *     .sheet("用户数据")
 *     .doRead(new ReadListener<User>() {
 *         @Override
 *         public void onData(AnalysisContext context, User data) {
 *             System.out.println(data);
 *         }
 *     });
 *
 * // 写入
 * ExcelFacade.write("output.xlsx", User.class)
 *     .sheet("用户列表")
 *     .doWrite(userList);
 * }</pre>
 *
 * @author ydsz-team
 * @version 1.0.0
 * @since 1.0.0
 * @see ExcelReader
 * @see ExcelWriter
 */
public class ExcelFacade {

    private ExcelFacade() {
    }

    // ==================== 读取相关方法 ====================

    /**
     * 从文件路径读取 Excel(无类型映射)
     *
     * @param fileName Excel 文件的完整路径，支持.xlsx 和.xls 格式
     * @return ExcelReader 读取器实例
     */
    public static ExcelReader read(String fileName) {
        return read(fileName, null);
    }

    /**
     * 从 File 对象读取 Excel(无类型映射)
     *
     * @param file Excel 文件的 File 对象
     * @return ExcelReader 读取器实例
     */
    public static ExcelReader read(File file) {
        return read(file, null);
    }

    /**
     * 从输入流读取 Excel(无类型映射)
     *
     * @param inputStream Excel 数据的输入流
     * @return ExcelReader 读取器实例
     */
    public static ExcelReader read(InputStream inputStream) {
        return read(inputStream, null);
    }

    /**
     * 从文件路径读取 Excel 并映射到指定类型
     *
     * @param fileName Excel 文件的完整路径
     * @param clazz 映射的目标类类型
     * @param <T> 泛型参数
     * @return ExcelReader 读取器实例
     */
    public static <T> ExcelReader read(String fileName, Class<T> clazz) {
        ReadMetadata metadata = new ReadMetadata();
        metadata.setClazz(clazz);
        metadata.setFilePath(fileName);
        return new ExcelReader(metadata);
    }

    /**
     * 从 File 对象读取 Excel 并映射到指定类型
     *
     * @param file Excel 文件的 File 对象
     * @param clazz 映射的目标类类型
     * @param <T> 泛型参数
     * @return ExcelReader 读取器实例
     */
    public static <T> ExcelReader read(File file, Class<T> clazz) {
        ReadMetadata metadata = new ReadMetadata();
        metadata.setClazz(clazz);
        metadata.setFile(file);
        return new ExcelReader(metadata);
    }

    /**
     * 从输入流读取 Excel 并映射到指定类型
     *
     * @param inputStream Excel 数据的输入流
     * @param clazz 映射的目标类类型
     * @param <T> 泛型参数
     * @return ExcelReader 读取器实例
     */
    public static <T> ExcelReader read(InputStream inputStream, Class<T> clazz) {
        ReadMetadata metadata = new ReadMetadata();
        metadata.setClazz(clazz);
        metadata.setInputStream(inputStream);
        return new ExcelReader(metadata);
    }

    // ==================== 写入相关方法 ====================

    /**
     * 创建 Excel 写入器 (无类型映射)
     *
     * @param fileName 目标 Excel 文件的完整路径
     * @return ExcelWriter 写入器实例
     */
    public static ExcelWriter write(String fileName) {
        return write(fileName, null);
    }

    /**
     * 从 File 对象创建写入器 (无类型映射)
     *
     * @param file 目标 Excel 文件的 File 对象
     * @return ExcelWriter 写入器实例
     */
    public static ExcelWriter write(File file) {
        return write(file, null);
    }

    /**
     * 从输出流创建写入器 (无类型映射)
     *
     * @param outputStream 目标输出流
     * @return ExcelWriter 写入器实例
     */
    public static ExcelWriter write(OutputStream outputStream) {
        return write(outputStream, null);
    }

    /**
     * 创建 Excel 写入器并指定映射类型
     *
     * @param fileName 目标 Excel 文件的完整路径
     * @param clazz 映射的源类类型
     * @param <T> 泛型参数
     * @return ExcelWriter 写入器实例
     */
    public static <T> ExcelWriter write(String fileName, Class<T> clazz) {
        WriteMetadata metadata = new WriteMetadata();
        metadata.setClazz(clazz);
        metadata.setFilePath(fileName);
        return new ExcelWriter(metadata);
    }

    /**
     * 创建 Excel 写入器并指定映射类型
     *
     * @param fileName 目标 Excel 文件的完整路径
     * @param clazz 映射的源类类型
     * @param <T> 泛型参数
     * @return ExcelWriter 写入器实例
     */
    public static <T> ExcelWriter write(String fileName, Class<T> clazz, int dataSize) {
        WriteMetadata metadata = new WriteMetadata();
        metadata.setClazz(clazz);
        metadata.setFilePath(fileName);
        metadata.setDataSize(dataSize);
        return new ExcelWriter(metadata);
    }

    /**
     * 从 File 对象创建写入器并指定映射类型
     *
     * @param file 目标 Excel 文件的 File 对象
     * @param clazz 映射的源类类型
     * @param <T> 泛型参数
     * @return ExcelWriter 写入器实例
     */
    public static <T> ExcelWriter write(File file, Class<T> clazz) {
        WriteMetadata metadata = new WriteMetadata();
        metadata.setClazz(clazz);
        metadata.setFile(file);
        return new ExcelWriter(metadata);
    }

    /**
     * 从输出流创建写入器并指定映射类型
     *
     * @param outputStream 目标输出流
     * @param clazz 映射的源类类型
     * @param <T> 泛型参数
     * @return ExcelWriter 写入器实例
     */
    public static <T> ExcelWriter write(OutputStream outputStream, Class<T> clazz) {
        WriteMetadata metadata = new WriteMetadata();
        metadata.setClazz(clazz);
        metadata.setOutputStream(outputStream);
        return new ExcelWriter(metadata);
    }

    // ==================== 多Sheet写入方法 ====================

    /**
     * 多Sheet写入 - 使用默认配置
     *
     * <p>用于一次性写入多个Sheet，每个Sheet对应一个数据列表。
     * 数据列表的顺序对应Sheet的顺序。
     * 注意: 此方法所有Sheet使用相同的类型clazz。</p>
     *
     * <h3>使用示例</h3>
     * <pre>{@code
     * List<Object> sheet1Data = ...; // 第一个Sheet的数据
     * List<Object> sheet2Data = ...; // 第二个Sheet的数据
     *
     * // 使用Map指定每个Sheet的名称和数据
     * Map<String, List<?>> sheets = new LinkedHashMap<>();
     * sheets.put("用户信息", sheet1Data);
     * sheets.put("部门信息", sheet2Data);
     * ExcelFacade.writeMultiple("output.xlsx", User.class, sheets);
     * }</pre>
     *
     * @param fileName 目标文件路径
     * @param clazz 默认的数据类型
     * @param sheets Map: Sheet名称 -> 数据列表
     */
    public static void writeMultiple(String fileName, Class<?> clazz, Map<String, ?> sheets) {
        if (sheets == null || sheets.isEmpty()) {
            return;
        }

        List<SheetData> sheetDataList = new ArrayList<>();
        for (Map.Entry<String, ?> entry : sheets.entrySet()) {
            SheetData sheetData = new SheetData(entry.getKey(), clazz, entry.getValue());
            sheetDataList.add(sheetData);
        }

        writeMultiple(fileName, sheetDataList);
    }

    /**
     * 多Sheet写入 - 使用SheetData列表
     *
     * <p>每个SheetData可以单独指定:
     * <ul>
     *   <li>Sheet名称</li>
     *   <li>数据类型</li>
     *   <li>数据列表</li>
     *   <li>表头行号</li>
     * </ul>
     *
     * @param fileName 目标文件路径
     * @param sheetDataList Sheet数据列表
     */
    public static void writeMultiple(String fileName, List<SheetData> sheetDataList) {
        if (sheetDataList == null || sheetDataList.isEmpty()) {
            return;
        }

        ExcelWriter firstWriter = null;
        int sheetIndex = 0;

        for (SheetData sheetData : sheetDataList) {
            ExcelWriter writer;
            if (sheetIndex == 0) {
                firstWriter = write(fileName, sheetData.getClazz());
                firstWriter.setMultiSheetWriting(true);
                firstWriter.sheet(sheetData.getSheetName() != null ? sheetData.getSheetName() : "sheet1");
                writer = firstWriter;
            } else {
                if (firstWriter == null) {
                    throw new IllegalStateException("ExcelWriter 未初始化，多Sheet写入必须从第一个Sheet开始");
                }
                writer = firstWriter.newSheet(sheetData.getSheetName() != null ? sheetData.getSheetName() : "sheet" + (sheetIndex + 1));
            }

            if (sheetData.getHeadRowNumber() != null) {
                writer.headRowNumber(sheetData.getHeadRowNumber());
            }

            writer.doWrite(sheetData.getData(), sheetIndex);
            sheetIndex++;
        }

        if (firstWriter != null) {
            try {
                firstWriter.finish();
            } catch (IOException e) {
                throw new RuntimeException("多Sheet写入失败", e);
            }
        }
    }



    // ==================== 模板写入 ====================

    /**
     * 基于模板写入Excel
     *
     * <p>将数据写入已有的Excel模板文件，保留模板中的样式、格式、公式等设置</p>
     *
     * @param templatePath 模板文件路径
     * @param outputPath 输出文件路径
     * @param clazz 数据类型
     * @return 模板写入器实例
     */
    public static ExcelTemplateWriter writeWithTemplate(String templatePath, String outputPath, Class<?> clazz) {
        return new ExcelTemplateWriter(templatePath, outputPath, clazz);
    }

}
