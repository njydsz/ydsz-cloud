package com.njydsz.common.excel.core;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.njydsz.common.excel.core.config.ExcelConfig;
import com.njydsz.common.excel.core.listener.ReadListener;
import com.njydsz.common.excel.core.metadata.ReadMetadata;
import com.njydsz.common.excel.core.metadata.WriteMetadata;
import com.njydsz.common.excel.core.model.SheetData;
import com.njydsz.common.excel.spring.DownloadContext;

/**
 * ExcelFacade - 高性能 Excel 处理工具
 *
 * <p>ExcelFacade 是整个框架的唯一入口类，提供简洁易用的 API 用于 Excel 文件的读取和写入操作。
 * 参照阿里巴巴 EasyExcel 的设计理念，注重性能优化和低内存占用。</p>
 *
 * <h2>核心特性</h2>
 * <ul>
 *   <li>低内存占用 - 基于 SAX 模式解析大文件 Excel</li>
 *   <li>流式写入 - 支持超大数据量的 Excel 写入</li>
 *   <li>注解驱动 - 通过简洁的注解配置实现字段映射</li>
 *   <li>类型安全 - 完善的数据类型转换机制</li>
 *   <li>Web 下载 - 便捷的 Web 环境 Excel 下载支持</li>
 * </ul>
 *
 * <h2>设计原则</h2>
 * <ul>
 *   <li>门面模式 - 统一入口，简化 API 调用</li>
 *   <li>构建器模式 - 链式调用，提升代码可读性</li>
 *   <li>策略模式 - 根据数据量自动选择最优写入策略</li>
 *   <li>观察者模式 - 监听器机制，支持数据流转处理</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <h3>读取 Excel</h3>
 * <pre>{@code
 * // 方式一：使用监听器读取
 * ExcelFacade.read("demo.xlsx", User.class)
 *     .sheet("用户数据")
 *     .doRead(new ReadListener<User>() {
 *         @Override
 *         public void onData(AnalysisContext context, User data) {
 *             System.out.println(data);
 *         }
 *     });
 *
 * // 方式二：快捷读取
 * ExcelFacade.read("demo.xlsx", User.class, (context, data) -> {
 *     System.out.println(data);
 * });
 * }</pre>
 *
 * <h3>写入 Excel</h3>
 * <pre>{@code
 * // 方式一：使用构建器模式
 * ExcelFacade.write("output.xlsx", User.class)
 *     .sheet("用户列表")
 *     .doWrite(userList);
 *
 * // 方式二：快捷写入
 * ExcelFacade.write("output.xlsx", User.class, userList);
 * }</pre>
 *
 * @author ydsz-team
 * @email ydsz-dev@njydsz.com
 * @version 1.0.0
 * @see ExcelReader
 * @see ExcelWriter
 * @since 1.0.0
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



    // ==================== 快捷方法 ====================

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

    /**
     * 获取Excel文件的Sheet信息列表
     *
     * @param fileName 文件路径
     * @return Sheet信息列表
     */
    public static List<ExcelSheetInfo> getSheetInfoList(String fileName) {
        return ExcelSheetInfo.getSheetInfoList(fileName);
    }

    /**
     * 获取Excel文件的Sheet数量
     *
     * @param fileName 文件路径
     * @return Sheet数量
     */
    public static int getSheetCount(String fileName) {
        return ExcelSheetInfo.getSheetCount(fileName);
    }

    /**
     * 获取Excel文件的Sheet名称列表
     *
     * @param fileName 文件路径
     * @return Sheet名称列表
     */
    public static List<String> getSheetNames(String fileName) {
        return ExcelSheetInfo.getSheetNames(fileName);
    }

    /**
     * 快捷写入方法 - 写入数据到指定文件
     *
     * @param fileName 目标文件路径
     * @param clazz 数据对象类型
     * @param data 要写入的数据
     */
    public static void write(String fileName, Class<?> clazz, Object data) {
        write(fileName, clazz).sheet("sheet1").doWrite(data);
    }

    /**
     * 快捷写入方法 - 写入数据到指定文件和 sheet
     *
     * @param fileName 目标文件路径
     * @param clazz 数据对象类型
     * @param data 要写入的数据
     * @param sheetName sheet 名称
     */
    public static void write(String fileName, Class<?> clazz, Object data, String sheetName) {
        write(fileName, clazz).sheet(sheetName).doWrite(data);
    }

    /**
     * 快捷读取方法 - 读取文件并处理每行数据
     *
     * @param fileName 源文件路径
     * @param clazz 数据映射类型
     * @param listener 数据读取监听器
     * @param <T> 泛型参数
     */
    public static <T> void read(String fileName, Class<T> clazz, ReadListener<T> listener) {
        read(fileName, clazz).sheet().doRead(listener);
    }

    /**
     * 快捷读取方法 - 从输入流读取并处理每行数据
     *
     * @param inputStream 源输入流
     * @param clazz 数据映射类型
     * @param listener 数据读取监听器
     * @param <T> 泛型参数
     */
    public static <T> void read(InputStream inputStream, Class<T> clazz, ReadListener<T> listener) {
        read(inputStream, clazz).sheet().doRead(listener);
    }

    // ==================== 配置相关方法 ====================

    /**
     * 设置全局配置
     *
     * @param configuration ExcelConfiguration 配置实例
     */
    public static void setConfiguration(ExcelConfig configuration) {
        ExcelConfig.setInstance(configuration);
    }

    /**
     * 获取当前全局配置
     *
     * @return ExcelConfiguration 配置实例
     */
    public static ExcelConfig getConfiguration() {
        return ExcelConfig.getInstance();
    }

    // ==================== Web 下载辅助方法 ====================

    /**
     * 生成 Web 下载响应的工具方法
     *
     * <p>设置 HTTP 响应头，使浏览器正确处理 Excel 文件的下载</p>
     *
     * @param fileName 下载文件名
     * @param isXlsx 是否为 xlsx 格式 (true=xlsx, false=xls)
     */
    public static void setWebResponse(String fileName, boolean isXlsx) {
        setWebResponse(fileName, isXlsx, null);
    }


    /**
     * Generate web download response.
     *
     * <p>Uses DownloadContext to manage download context, unified ThreadLocal management.</p>
     *
     * @param fileName download file name
     * @param isXlsx whether xlsx format
     * @param contentType custom content type (deprecated, handled by ExcelWebSupport)
     */
    public static void setWebResponse(String fileName, boolean isXlsx, String contentType) {
        DownloadContext.setFileName(
            fileName + (isXlsx ? ".xlsx" : ".xls"));
    }

}
