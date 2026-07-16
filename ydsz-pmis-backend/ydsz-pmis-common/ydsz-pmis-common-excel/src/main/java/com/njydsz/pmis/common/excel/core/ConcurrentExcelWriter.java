package com.njydsz.pmis.common.excel.core;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.pmis.common.excel.annotation.ExcelIgnore;
import com.njydsz.pmis.common.excel.annotation.ExcelProperty;
import com.njydsz.pmis.common.excel.core.config.ExcelConfig;

/**
 * 并发 Excel 写入器 — 多线程分片预序列化 + 顺序写入
 *
 * <p>将大数据集分片后使用多线程并行预序列化为 XML 字节，然后顺序写入 ZIP 文件。
 * 由于 OOXML 格式要求 ZIP 内条目顺序写入，本类采用「并行序列化 + 顺序写入」策略，
 * 在 CPU 密集型场景下可获得接近线性的加速比。</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * ConcurrentExcelWriter.write("output.xlsx", User.class, largeDataList)
 *     .parallelism(4)
 *     .chunkSize(10000)
 *     .doWrite();
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class ConcurrentExcelWriter {

    private static final Logger log = LoggerFactory.getLogger(ConcurrentExcelWriter.class);

    private static final int ZIP_BUFFER_SIZE = 1024 * 1024;

    private final String filePath;
    private final Class<?> clazz;
    private final List<?> data;
    private int parallelism = Runtime.getRuntime().availableProcessors();
    private int chunkSize = 10000;

    private ConcurrentExcelWriter(String filePath, Class<?> clazz, List<?> data) {
        this.filePath = filePath;
        this.clazz = clazz;
        this.data = data;
    }

    /**
     * 创建并发写入器
     *
     * @param filePath 输出文件路径
     * @param clazz    数据类型
     * @param data     数据列表
     * @return 写入器实例
     */
    public static ConcurrentExcelWriter write(String filePath, Class<?> clazz, List<?> data) {
        return new ConcurrentExcelWriter(filePath, clazz, data);
    }

    /**
     * 设置并行度
     *
     * @param parallelism 线程数
     * @return this
     */
    public ConcurrentExcelWriter parallelism(int parallelism) {
        this.parallelism = Math.max(1, parallelism);
        return this;
    }

    /**
     * 设置分片大小
     *
     * @param chunkSize 每片数据行数
     * @return this
     */
    public ConcurrentExcelWriter chunkSize(int chunkSize) {
        this.chunkSize = Math.max(100, chunkSize);
        return this;
    }

    /**
     * 执行并发写入
     *
     * <p>将数据分片后并行预序列化为字节数组，然后顺序写入 ZIP 文件。
     * 适用于 10 万行以上的大数据量场景。</p>
     */
    public void doWrite() {
        int totalSize = data.size();
        if (totalSize <= chunkSize) {
            // 小数据集直接使用单线程快速写入
            ExcelFacade.write(filePath, clazz, data, "Sheet1");
            return;
        }

        // 计算分片数
        int chunkCount = (totalSize + chunkSize - 1) / chunkSize;
        log.info("并发写入: 总行数={}, 分片数={}, 并行度={}, 分片大小={}",
                totalSize, chunkCount, parallelism, chunkSize);

        // 创建线程池
        ExecutorService executor = Executors.newFixedThreadPool(parallelism, new NamedThreadFactory("excel-writer"));

        try {
            // 并行预序列化每个分片
            List<CompletableFuture<ChunkResult>> futures = new ArrayList<>(chunkCount);
            for (int i = 0; i < chunkCount; i++) {
                final int chunkIndex = i;
                final int start = i * chunkSize;
                final int end = Math.min(start + chunkSize, totalSize);
                final List<?> chunk = data.subList(start, end);

                futures.add(CompletableFuture.supplyAsync(() -> serializeChunk(chunkIndex, chunk), executor));
            }

            // 等待所有分片完成并收集结果
            List<ChunkResult> results = new ArrayList<>(chunkCount);
            for (CompletableFuture<ChunkResult> future : futures) {
                results.add(future.join());
            }

            // 按分片顺序排序
            results.sort(Comparator.comparingInt(r -> r.getChunkIndex()));

            // 顺序写入最终文件
            writeMergedFile(results);

            log.info("并发写入完成: 输出文件={}", filePath);
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 序列化单个分片为 XML 字节
     */
    private ChunkResult serializeChunk(int chunkIndex, List<?> chunk) {
        try {
            StringBuilder sb = new StringBuilder(chunk.size() * 256);
            int rowNum = chunkIndex * chunkSize + 1; // +1 for header row

            for (Object item : chunk) {
                rowNum++;
                sb.append("<row r=\"").append(rowNum).append("\">");

                // 使用反射获取字段值并序列化
                if (item != null) {
                    var fields = clazz.getDeclaredFields();
                    int col = 0;
                    for (var field : fields) {
                        field.setAccessible(true);
                        var ignore = field.getAnnotation(ExcelIgnore.class);
                        if (ignore != null) continue;

                        var prop = field.getAnnotation(ExcelProperty.class);
                        if (prop == null) continue;

                        Object value = field.get(item);
                        if (value != null) {
                            String cellRef = toCellRef(col) + rowNum;
                            String escapedValue = escapeXml(value.toString());
                            sb.append("<c r=\"").append(cellRef).append("\" t=\"inlineStr\">")
                              .append("<is><t>").append(escapedValue).append("</t></is>")
                              .append("</c>");
                        }
                        col++;
                    }
                }
                sb.append("</row>");
            }

            byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
            return new ChunkResult(chunkIndex, bytes);
        } catch (Exception e) {
            throw new RuntimeException("分片序列化失败: chunkIndex=" + chunkIndex, e);
        }
    }

    /**
     * 将合并后的分片写入最终 xlsx 文件
     */
    private void writeMergedFile(List<ChunkResult> results) {
        // 使用 SuperFastExcelWriter 的静态模板
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("ydsz_concurrent_");

            // 构建 SharedStrings
            // 这里简化处理，使用 inlineStr 方式避免 SST 构建

            try (FileOutputStream fos = new FileOutputStream(filePath);
                 BufferedOutputStream bos = new BufferedOutputStream(fos, ZIP_BUFFER_SIZE);
                 ZipOutputStream zipOut = new ZipOutputStream(bos)) {

                zipOut.setLevel(ExcelConfig.getInstance().getCompressionLevel());

                // [Content_Types].xml
                zipOut.putNextEntry(new ZipEntry("[Content_Types].xml"));
                zipOut.write(CONTENT_TYPES);
                zipOut.closeEntry();

                // _rels/.rels
                zipOut.putNextEntry(new ZipEntry("_rels/.rels"));
                zipOut.write(RELS);
                zipOut.closeEntry();

                // xl/_rels/workbook.xml.rels
                zipOut.putNextEntry(new ZipEntry("xl/_rels/workbook.xml.rels"));
                zipOut.write(WORKBOOK_RELS);
                zipOut.closeEntry();

                // xl/workbook.xml
                zipOut.putNextEntry(new ZipEntry("xl/workbook.xml"));
                zipOut.write(WORKBOOK);
                zipOut.closeEntry();

                // xl/worksheets/sheet1.xml — 写入表头 + 分片数据
                zipOut.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
                zipOut.write(SHEET_HEADER);

                // 写入表头行
                byte[] headerBytes = buildHeaderRow();
                if (headerBytes != null) {
                    zipOut.write(headerBytes);
                }

                // 顺序写入各分片
                for (ChunkResult result : results) {
                    zipOut.write(result.getBytes());
                }

                zipOut.write(SHEET_FOOTER);
                zipOut.closeEntry();

                // xl/sharedStrings.xml (空 SST，因为使用 inlineStr)
                zipOut.putNextEntry(new ZipEntry("xl/sharedStrings.xml"));
                zipOut.write(EMPTY_SST);
                zipOut.closeEntry();

                zipOut.finish();
            }
        } catch (Exception e) {
            throw new RuntimeException("并发写入文件失败: " + filePath, e);
        } finally {
            if (tempDir != null) {
                try {
                    Files.walk(tempDir)
                         .sorted(Comparator.reverseOrder())
                         .map(p -> p.toFile())
                         .forEach(f -> f.delete());
                } catch (Exception e) {
                    log.warn("清理临时文件异常", e);
                }
            }
        }
    }

    /**
     * 构建表头行 XML
     */
    private byte[] buildHeaderRow() {
        try {
            var fields = clazz.getDeclaredFields();
            StringBuilder sb = new StringBuilder(256);
            sb.append("<row r=\"1\">");

            int col = 0;
            for (var field : fields) {
                var ignore = field.getAnnotation(ExcelIgnore.class);
                if (ignore != null) continue;

                var prop = field.getAnnotation(ExcelProperty.class);
                if (prop == null) continue;

                String headerName = (prop.value() != null && !prop.value().isEmpty())
                        ? prop.value() : field.getName();
                String cellRef = toCellRef(col) + "1";
                sb.append("<c r=\"").append(cellRef).append("\" t=\"inlineStr\">")
                  .append("<is><t>").append(escapeXml(headerName)).append("</t></is>")
                  .append("</c>");
                col++;
            }
            sb.append("</row>");
            return sb.toString().getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("构建表头行失败", e);
            return null;
        }
    }

    /**
     * 列号转字母引用（A, B, ..., Z, AA, AB, ...）
     */
    private static String toCellRef(int col) {
        StringBuilder sb = new StringBuilder();
        int c = col;
        while (c >= 0) {
            sb.insert(0, (char) ('A' + c % 26));
            c = c / 26 - 1;
        }
        return sb.toString();
    }

    /**
     * XML 转义
     */
    private static String escapeXml(String str) {
        if (str == null) return "";
        return str.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;")
                  .replace("\"", "&quot;")
                  .replace("'", "&apos;");
    }

    // ==================== 静态 XML 模板 ====================

    private static final byte[] CONTENT_TYPES =
            ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
             "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
             "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
             "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
             "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
             "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
             "<Override PartName=\"/xl/sharedStrings.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml\"/>" +
             "</Types>").getBytes(StandardCharsets.UTF_8);

    private static final byte[] RELS =
            ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
             "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
             "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
             "</Relationships>").getBytes(StandardCharsets.UTF_8);

    private static final byte[] WORKBOOK_RELS =
            ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
             "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
             "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
             "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings\" Target=\"sharedStrings.xml\"/>" +
             "</Relationships>").getBytes(StandardCharsets.UTF_8);

    private static final byte[] WORKBOOK =
            ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
             "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
             "<sheets><sheet name=\"Sheet1\" sheetId=\"1\" r:id=\"rId1\"/></sheets>" +
             "</workbook>").getBytes(StandardCharsets.UTF_8);

    private static final byte[] SHEET_HEADER =
            ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
             "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
             "<sheetData>").getBytes(StandardCharsets.UTF_8);

    private static final byte[] SHEET_FOOTER = "</sheetData></worksheet>".getBytes(StandardCharsets.UTF_8);

    private static final byte[] EMPTY_SST =
            ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
             "<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" count=\"0\" uniqueCount=\"0\"/>")
                    .getBytes(StandardCharsets.UTF_8);

    // ==================== 内部类 ====================

    /**
     * 分片序列化结果
     */
    private static class ChunkResult {
        private final int chunkIndex;
        private final byte[] bytes;

        ChunkResult(int chunkIndex, byte[] bytes) {
            this.chunkIndex = chunkIndex;
            this.bytes = bytes;
        }

        int getChunkIndex() {
            return chunkIndex;
        }

        byte[] getBytes() {
            return bytes;
        }
    }

    /**
     * 命名线程工厂
     */
    private static class NamedThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger(0);
        private final String prefix;

        NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, prefix + "-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    }
}
