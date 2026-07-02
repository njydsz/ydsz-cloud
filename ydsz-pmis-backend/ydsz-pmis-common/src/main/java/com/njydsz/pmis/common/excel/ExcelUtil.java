package com.njydsz.pmis.common.excel;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * EasyExcel 通用工具
 *
 * <p>封装基于 EasyExcel 的导入/导出常见用法。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public final class ExcelUtil {

    private ExcelUtil() {
    }

    // ==================== 导出 ====================

    /**
     * 导出到 HTTP 响应（浏览器下载）
     *
     * @param response  HTTP 响应对象
     * @param fileName  下载文件名（会被 URL 编码）
     * @param sheetName Sheet 名称，为 null 时使用 "Sheet1"
     * @param headClass 表头类型（{@code @ExcelProperty} 标注的 DTO）
     * @param data      数据列表
     * @param <T>       数据类型
     * @throws IOException 写入响应流失败时抛出
     */
    public static <T> void export(HttpServletResponse response, String fileName,
                                  String sheetName, Class<T> headClass, List<T> data) throws IOException {
        setDownloadHeader(response, fileName);
        try (OutputStream out = response.getOutputStream()) {
            EasyExcel.write(out, headClass)
                    .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                    .sheet(sheetName == null ? "Sheet1" : sheetName)
                    .doWrite(data);
        }
    }

    /**
     * 导出到字节数组
     *
     * @param sheetName Sheet 名称，为 null 时使用 "Sheet1"
     * @param headClass 表头类型
     * @param data      数据列表
     * @param <T>       数据类型
     * @return 生成的 xlsx 字节数组
     * @throws IOException 写入流失败时抛出
     */
    public static <T> byte[] exportToBytes(String sheetName, Class<T> headClass, List<T> data) throws IOException {
        try (java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            EasyExcel.write(out, headClass)
                    .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                    .sheet(sheetName == null ? "Sheet1" : sheetName)
                    .doWrite(data);
            return out.toByteArray();
        }
    }

    /**
     * 分 Sheet 导出大数据量
     *
     * @param out       输出流（由调用方负责关闭）
     * @param headClass 表头类型
     * @param sheets    多个 Sheet 数据
     * @param <T>       数据类型
     */
    public static <T> void exportMultiSheet(OutputStream out, Class<T> headClass,
                                            List<ExcelSheet<T>> sheets) {
        try (ExcelWriter writer = EasyExcel.write(out, headClass)
                .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                .build()) {
            for (int i = 0; i < sheets.size(); i++) {
                ExcelSheet<T> s = sheets.get(i);
                writer.write(s.getData(), EasyExcel.writerSheet(i, s.getName()).build());
            }
        }
    }

    // ==================== 导入 ====================

    /**
     * 读取所有行（适合小数据量）
     *
     * @param file      上传的 Excel 文件
     * @param headClass 表头类型
     * @param <T>       数据类型
     * @return 全部数据行列表
     * @throws IOException 读取文件失败时抛出
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> List<T> readAll(MultipartFile file, Class<T> headClass) throws IOException {
        List list = new ArrayList();
        try (InputStream in = file.getInputStream()) {
            EasyExcel.read(in, headClass, new SimpleReadListener(list::addAll))
                    .sheet().doRead();
        }
        return list;
    }

    /**
     * 流式读取（适合大数据量）
     *
     * @param file      上传的 Excel 文件
     * @param headClass 表头类型
     * @param consumer  每行数据的回调消费器
     * @param <T>       数据类型
     * @throws IOException 读取文件失败时抛出
     */
    @SuppressWarnings("unchecked")
    public static <T> void readStreaming(MultipartFile file, Class<T> headClass,
                                         Consumer<T> consumer) throws IOException {
        try (InputStream in = file.getInputStream()) {
            EasyExcel.read(in, headClass, new SimpleReadListener(batch -> {
                for (Object o : batch) {
                    consumer.accept((T) o);
                }
            })).sheet().doRead();
        }
    }

    // ==================== 工具 ====================

    /**
     * 设置 HTTP 下载响应头（Content-Type / Content-Disposition）
     *
     * @param response HTTP 响应对象
     * @param fileName 下载文件名（会被 URL 编码）
     * @throws IOException 设置响应头失败时抛出
     */
    private static void setDownloadHeader(HttpServletResponse response, String fileName) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded);
    }

    /**
     * 简单的读监听器：每 100 行触发一次 batch
     */
    public static class SimpleReadListener implements ReadListener<Object> {
        /** 批量数据回调消费器 */
        private final java.util.function.Consumer<List<Object>> sink;
        /** 当前批次缓冲区 */
        private final List<Object> buffer = new ArrayList<>(128);
        /** 触发批量回调的阈值行数 */
        private static final int BATCH = 100;

        /**
         * @param sink 批量数据回调消费器
         */
        public SimpleReadListener(java.util.function.Consumer<List<Object>> sink) {
            this.sink = sink;
        }

        /**
         * 每解析一行调用一次，缓冲达到 BATCH 阈值时触发批量回调
         *
         * @param data    当前行数据
         * @param context 解析上下文
         */
        @Override
        public void invoke(Object data, AnalysisContext context) {
            buffer.add(data);
            if (buffer.size() >= BATCH) {
                flush();
            }
        }

        /**
         * 全部解析完成后触发剩余数据的批量回调
         *
         * @param context 解析上下文
         */
        @Override
        public void doAfterAllAnalysed(AnalysisContext context) {
            flush();
        }

        /**
         * 将缓冲区数据交给回调并清空缓冲
         */
        private void flush() {
            if (!buffer.isEmpty()) {
                sink.accept(new ArrayList<>(buffer));
                buffer.clear();
            }
        }
    }

    /**
     * Sheet 数据
     */
    public static class ExcelSheet<T> {
        /** Sheet 名 */
        private final String name;
        /** Sheet 数据 */
        private final List<T> data;

        /**
         * @param name Sheet 名
         * @param data Sheet 数据
         */
        public ExcelSheet(String name, List<T> data) {
            this.name = name;
            this.data = data;
        }

        /**
         * @return Sheet 名
         */
        public String getName() {
            return name;
        }

        /**
         * @return Sheet 数据
         */
        public List<T> getData() {
            return data;
        }
    }
}
