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
     */
    @SuppressWarnings("rawtypes")
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
        private final java.util.function.Consumer<List<Object>> sink;
        private final List<Object> buffer = new ArrayList<>(128);
        private static final int BATCH = 100;

        public SimpleReadListener(java.util.function.Consumer<List<Object>> sink) {
            this.sink = sink;
        }

        @Override
        public void invoke(Object data, AnalysisContext context) {
            buffer.add(data);
            if (buffer.size() >= BATCH) {
                flush();
            }
        }

        @Override
        public void doAfterAllAnalysed(AnalysisContext context) {
            flush();
        }

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
        private final String name;
        private final List<T> data;

        public ExcelSheet(String name, List<T> data) {
            this.name = name;
            this.data = data;
        }

        public String getName() {
            return name;
        }

        public List<T> getData() {
            return data;
        }
    }
}
