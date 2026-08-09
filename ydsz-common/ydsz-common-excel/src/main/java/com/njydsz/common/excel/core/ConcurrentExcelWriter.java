package com.njydsz.common.excel.core;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Set;
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

import com.njydsz.common.excel.annotation.ExcelIgnore;
import com.njydsz.common.excel.annotation.ExcelProperty;
import com.njydsz.common.excel.annotation.ExcelSheet;
import com.njydsz.common.excel.core.config.ExcelConfig;
import com.njydsz.common.excel.core.security.FormulaInjectionGuard;
import com.njydsz.common.excel.support.asm.ASMFieldAccessor;
import com.njydsz.common.excel.support.asm.ASMFieldAccessor.FieldGetter;
import com.njydsz.common.excel.support.cache.ReflectCache;

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
 * @author ydsz-team
 * @since 1.0.0
 */
public class ConcurrentExcelWriter {

    private static final Logger log = LoggerFactory.getLogger(ConcurrentExcelWriter.class);

    private static final int ZIP_BUFFER_SIZE = 1024 * 1024;
    private static final DateTimeFormatter DEFAULT_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String filePath;
    private final Class<?> clazz;
    private final List<?> data;
    private int parallelism = Runtime.getRuntime().availableProcessors();
    private int chunkSize = 10000;
    private Set<String> excludeColumnFiledNames;
    private Set<String> includeColumnFiledNames;

    private FieldAccessorInfo[] fieldInfos;
    private int fieldInfoSize;

    private ConcurrentExcelWriter(String filePath, Class<?> clazz, List<?> data) {
        this.filePath = filePath;
        this.clazz = clazz;
        this.data = data;
        if (clazz != null) {
            analyzeClass();
        }
    }

    /**
     * 创建并发写入器实例。
     *
     * <p>返回的实例是链式配置入口，需再调用 {@link #doWrite()} 才真正落盘。
     * {@code clazz} 可为 {@code null}，但此时基于 {@link ExcelProperty} 注解的
     * 列解析、过滤与排序均不生效。
     *
     * @param filePath 输出 xlsx 文件路径
     * @param clazz    数据模型类，用于解析列元数据，可为 {@code null}
     * @param data     待写入数据；可为空列表（空列表短路返回、不生成文件）
     * @return 并发写入器实例
     */
    public static ConcurrentExcelWriter write(String filePath, Class<?> clazz, List<?> data) {
        return new ConcurrentExcelWriter(filePath, clazz, data);
    }

    /**
     * 设置并行序列化线程数。
     *
     * <p>仅在数据量超过 {@link #chunkSize(int)} 时才生效；小于 1 按 1 处理，即退化为
     * 单线程。建议不超过可用 CPU 核数，否则线程切换开销会抵消并行收益。
     *
     * @param parallelism 期望并行度，下限为 1
     * @return 当前写入器，便于链式调用
     */
    public ConcurrentExcelWriter parallelism(int parallelism) {
        this.parallelism = Math.max(1, parallelism);
        return this;
    }

    /**
     * 设置分片大小（行数）。
     *
     * <p>数据按该大小切分为多个分片并行序列化；小于 100 按 100 处理。分片过小会放大
     * 线程调度与结果重排开销，过大则降低并行度，建议取值范围 5000~20000。
     *
     * @param chunkSize 每个分片的行数，下限为 100
     * @return 当前写入器，便于链式调用
     */
    public ConcurrentExcelWriter chunkSize(int chunkSize) {
        this.chunkSize = Math.max(100, chunkSize);
        return this;
    }

    /**
     * 设置排除列名集合，命中这些字段名的列不写入 Excel。
     *
     * <p>在 {@link ExcelIgnore} / {@link ExcelProperty#ignore()} 过滤之后、排序之前生效；
     * 与 {@link #includeColumnFiledNames(Set)} 同时设置时排除优先。设置后会重新解析
     * 类元数据，因此需在 {@link #doWrite()} 之前调用。
     *
     * @param excludeColumnFiledNames 需要排除的字段名集合，可为 {@code null}（不排除任何列）
     * @return 当前写入器，便于链式调用
     */
    public ConcurrentExcelWriter excludeColumnFiledNames(Set<String> excludeColumnFiledNames) {
        this.excludeColumnFiledNames = excludeColumnFiledNames;
        if (clazz != null) {
            analyzeClass();
        }
        return this;
    }

    /**
     * 设置包含列名白名单，仅这些字段名对应的列会写入 Excel。
     *
     * <p>与 {@link #excludeColumnFiledNames(Set)} 的排除语义相反，两者同时设置时排除优先；
     * 集合为空视为不限制。设置后会重新解析类元数据，因此需在 {@link #doWrite()} 之前调用。
     *
     * @param includeColumnFiledNames 允许写入的字段名白名单，可为 {@code null} 或空集（全部写入）
     * @return 当前写入器，便于链式调用
     */
    public ConcurrentExcelWriter includeColumnFiledNames(Set<String> includeColumnFiledNames) {
        this.includeColumnFiledNames = includeColumnFiledNames;
        if (clazz != null) {
            analyzeClass();
        }
        return this;
    }

    /**
     * 执行写入，将数据落盘为 xlsx 文件。
     *
     * <p><b>小数据量短路</b>：当总行数不超过 {@code chunkSize} 时，并行拆分收益不抵线程池开销，
     * 直接委派给 {@link ExcelFacade#write} 单线程写入。
     *
     * <p><b>并行策略</b>：超过阈值时按 {@code chunkSize} 切片，提交到固定大小线程池并行序列化为
     * XML 字节；由于 OOXML 要求 ZIP 条目顺序写入，各分片结果会先按 {@code chunkIndex}
     * 重排序，再单线程顺序写入同一个 sheet，从而保证输出行序与入参 {@code data} 完全一致。
     *
     * <p><b>资源与线程</b>：线程池为方法内私有、全部为 daemon 线程，{@code finally} 中先优雅
     * 关闭，5 秒未终止则强制 {@code shutdownNow}；等待被中断时会强制关闭并恢复中断标记。
     * 本方法非线程安全，同一实例不应并发调用。
     *
     * <p><b>失败语义</b>：任一分片序列化异常会通过 {@code CompletableFuture#join} 以
     * {@link java.util.concurrent.CompletionException} 向外抛出，此时目标文件尚未创建；
     * 而合并落盘阶段失败则可能残留不完整文件，需调用方自行清理。
     * 单个字段取值异常不会中断整体写入，仅记录 warn 日志并将该单元格留空。
     *
     * @throws RuntimeException 分片序列化失败或合并写文件失败时抛出，原始异常置于 cause
     */
    public void doWrite() {
        int totalSize = data.size();
        if (totalSize <= chunkSize) {
            String sheetName = resolveSheetName();
            ExcelFacade.write(filePath, clazz, data, sheetName);
            return;
        }

        int chunkCount = (totalSize + chunkSize - 1) / chunkSize;
        log.info("并发写入: 总行数={}, 分片数={}, 并行度={}, 分片大小={}",
                totalSize, chunkCount, parallelism, chunkSize);

        ExecutorService executor = Executors.newFixedThreadPool(parallelism, new NamedThreadFactory("excel-writer"));

        try {
            List<CompletableFuture<ChunkResult>> futures = new ArrayList<>(chunkCount);
            for (int i = 0; i < chunkCount; i++) {
                final int chunkIndex = i;
                final int start = i * chunkSize;
                final int end = Math.min(start + chunkSize, totalSize);
                final List<?> chunk = data.subList(start, end);
                futures.add(CompletableFuture.supplyAsync(() -> serializeChunk(chunkIndex, chunk), executor));
            }

            List<ChunkResult> results = new ArrayList<>(chunkCount);
            for (CompletableFuture<ChunkResult> future : futures) {
                Response.add(future.join());
            }

            Response.sort(Comparator.comparingInt(ChunkResult::getChunkIndex));
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

    private String resolveSheetName() {
        if (clazz != null) {
            ExcelSheet sheetAnnotation = clazz.getAnnotation(ExcelSheet.class);
            if (sheetAnnotation != null && !sheetAnnotation.name().isEmpty()) {
                return sheetAnnotation.name();
            }
        }
        return "Sheet1";
    }

    private void analyzeClass() {
        Field[] fields = ReflectCache.getCachedFields(clazz);
        List<Field> annotatedFields = new ArrayList<>();

        for (Field field : fields) {
            if (field.isAnnotationPresent(ExcelIgnore.class)) {
                continue;
            }
            ExcelProperty prop = field.getAnnotation(ExcelProperty.class);
            if (prop == null || prop.ignore()) {
                continue;
            }

            String fieldName = field.getName();
            if (excludeColumnFiledNames != null && excludeColumnFiledNames.contains(fieldName)) {
                continue;
            }
            if (includeColumnFiledNames != null && !includeColumnFiledNames.isEmpty()
                    && !includeColumnFiledNames.contains(fieldName)) {
                continue;
            }
            annotatedFields.add(field);
        }

        annotatedFields.sort(Comparator.comparingInt(f -> {
            ExcelProperty ann = f.getAnnotation(ExcelProperty.class);
            return ann.order();
        }));

        fieldInfoSize = annotatedFields.size();
        fieldInfos = new FieldAccessorInfo[fieldInfoSize];

        for (int i = 0; i < fieldInfoSize; i++) {
            Field field = annotatedFields.get(i);
            ExcelProperty prop = field.getAnnotation(ExcelProperty.class);

            FieldAccessorInfo info = new FieldAccessorInfo();
            info.fieldName = field.getName();
            info.headerName = (prop.value() != null && !prop.value().isEmpty())
                    ? prop.value() : field.getName();
            info.getter = ASMFieldAccessor.getGetter(clazz, field);
            info.fieldType = field.getType();

            String dateFormat = prop.dateFormat();
            info.dateFormat = (dateFormat != null && !dateFormat.isEmpty())
                    ? DateTimeFormatter.ofPattern(dateFormat)
                    : null;
            fieldInfos[i] = info;
        }
    }

    private ChunkResult serializeChunk(int chunkIndex, List<?> chunk) {
        try {
            StringBuilder sb = new StringBuilder(chunk.size() * 256);
            int rowNum = chunkIndex * chunkSize + 1;

            for (Object item : chunk) {
                rowNum++;
                sb.append("<row r=\"").append(rowNum).append("\">");

                if (item != null) {
                    for (int col = 0; col < fieldInfoSize; col++) {
                        FieldAccessorInfo info = fieldInfos[col];
                        if (info == null) continue;

                        Object value;
                        try {
                            value = info.getter.get(item);
                        } catch (Exception e) {
                            log.warn("获取字段值异常: field={}", info.fieldName, e);
                            continue;
                        }

                        if (value != null) {
                            appendCellXml(sb, col, rowNum, value, info);
                        }
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

    private void appendCellXml(StringBuilder sb, int col, int rowNum, Object value, FieldAccessorInfo info) {
        String cellRef = toCellRef(col) + rowNum;
        boolean formulaProtection = ExcelConfig.getInstance().isFormulaInjectionProtection();

        if (value instanceof String s) {
            String processed = formulaProtection ? FormulaInjectionGuard.sanitizeFormulaInjection(s) : s;
            sb.append("<c r=\"").append(cellRef).append("\" t=\"inlineStr\">")
              .append("<is><t>").append(escapeXml(processed)).append("</t></is>")
              .append("</c>");
        } else if (value instanceof Number n) {
            sb.append("<c r=\"").append(cellRef).append("\"><v>")
              .append(n.toString())
              .append("</v></c>");
        } else if (value instanceof Boolean b) {
            sb.append("<c r=\"").append(cellRef).append("\" t=\"b\"><v>")
              .append(b ? "1" : "0")
              .append("</v></c>");
        } else if (value instanceof LocalDateTime ldt) {
            DateTimeFormatter fmt = info.dateFormat != null ? info.dateFormat : DEFAULT_DATE_FORMATTER;
            sb.append("<c r=\"").append(cellRef).append("\" t=\"inlineStr\">")
              .append("<is><t>").append(escapeXml(ldt.format(fmt))).append("</t></is>")
              .append("</c>");
        } else if (value instanceof LocalDate ld) {
            DateTimeFormatter fmt = info.dateFormat != null ? info.dateFormat : DEFAULT_DATE_FORMATTER;
            sb.append("<c r=\"").append(cellRef).append("\" t=\"inlineStr\">")
              .append("<is><t>").append(escapeXml(ld.format(fmt))).append("</t></is>")
              .append("</c>");
        } else if (value instanceof Date d) {
            DateTimeFormatter fmt = info.dateFormat != null ? info.dateFormat : DEFAULT_DATE_FORMATTER;
            String dateStr = d.toInstant().atZone(ZoneId.systemDefault()).format(fmt);
            sb.append("<c r=\"").append(cellRef).append("\" t=\"inlineStr\">")
              .append("<is><t>").append(escapeXml(dateStr)).append("</t></is>")
              .append("</c>");
        } else {
            String strValue = value.toString();
            if (formulaProtection) {
                strValue = FormulaInjectionGuard.sanitizeFormulaInjection(strValue);
            }
            sb.append("<c r=\"").append(cellRef).append("\" t=\"inlineStr\">")
              .append("<is><t>").append(escapeXml(strValue)).append("</t></is>")
              .append("</c>");
        }
    }

    private void writeMergedFile(List<ChunkResult> results) {
        String sheetName = resolveSheetName();
        byte[] workbookBytes = buildWorkbookXml(sheetName);

        try (FileOutputStream fos = new FileOutputStream(filePath);
             BufferedOutputStream bos = new BufferedOutputStream(fos, ZIP_BUFFER_SIZE);
             ZipOutputStream zipOut = new ZipOutputStream(bos)) {

            zipOut.setLevel(ExcelConfig.getInstance().getCompressionLevel());

            zipOut.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zipOut.write(CONTENT_TYPES);
            zipOut.closeEntry();

            zipOut.putNextEntry(new ZipEntry("_rels/.rels"));
            zipOut.write(RELS);
            zipOut.closeEntry();

            zipOut.putNextEntry(new ZipEntry("xl/_rels/workbook.xml.rels"));
            zipOut.write(WORKBOOK_RELS);
            zipOut.closeEntry();

            zipOut.putNextEntry(new ZipEntry("xl/workbook.xml"));
            zipOut.write(workbookBytes);
            zipOut.closeEntry();

            zipOut.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
            zipOut.write(SHEET_HEADER);

            byte[] headerBytes = buildHeaderRow();
            if (headerBytes != null) {
                zipOut.write(headerBytes);
            }

            for (ChunkResult result : results) {
                zipOut.write(result.getBytes());
            }

            zipOut.write(SHEET_FOOTER);
            zipOut.closeEntry();

            zipOut.putNextEntry(new ZipEntry("xl/sharedStrings.xml"));
            zipOut.write(EMPTY_SST);
            zipOut.closeEntry();

            zipOut.finish();
        } catch (Exception e) {
            throw new RuntimeException("并发写入文件失败: " + filePath, e);
        }
    }

    private byte[] buildWorkbookXml(String sheetName) {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
             "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" " +
             "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
             "<sheets><sheet name=\"" + escapeXml(sheetName) + "\" sheetId=\"1\" r:id=\"rId1\"/></sheets>" +
             "</workbook>";
        return xml.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] buildHeaderRow() {
        if (fieldInfos == null || fieldInfoSize == 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder(256);
        sb.append("<row r=\"1\">");

        for (int col = 0; col < fieldInfoSize; col++) {
            FieldAccessorInfo info = fieldInfos[col];
            if (info == null) continue;

            String cellRef = toCellRef(col) + "1";
            sb.append("<c r=\"").append(cellRef).append("\" t=\"inlineStr\">")
              .append("<is><t>").append(escapeXml(info.headerName)).append("</t></is>")
              .append("</c>");
        }
        sb.append("</row>");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String toCellRef(int col) {
        StringBuilder sb = new StringBuilder();
        int c = col;
        while (c >= 0) {
            sb.insert(0, (char) ('A' + c % 26));
            c = c / 26 - 1;
        }
        return sb.toString();
    }

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
     * 单个分片序列化结果，携带分片序号以便合并阶段重排序。
     *
     * <p>由各并行分片任务产出，合并写入前按 {@code chunkIndex} 升序排列，
     * 保证最终行序与入参 {@code data} 完全一致。
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
     * 为并发写入线程池提供命名的 daemon 线程工厂。
     *
     * <p>线程名形如 {@code prefix-序号}；线程全部设为 daemon，避免阻塞 JVM 正常退出。
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

    /**
     * 单列访问元数据，绑定字段名、表头文本、ASM 访问器与日期格式。
     *
     * <p>在 {@link #analyzeClass()} 阶段构建，供各分片序列化线程只读共享。
     */
    private static class FieldAccessorInfo {
        String fieldName;
        String headerName;
        FieldGetter getter;
        Class<?> fieldType;
        DateTimeFormatter dateFormat;
    }
}
