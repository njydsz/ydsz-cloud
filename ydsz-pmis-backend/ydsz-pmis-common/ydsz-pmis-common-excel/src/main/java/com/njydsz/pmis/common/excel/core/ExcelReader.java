package com.njydsz.pmis.common.excel.core;

import java.io.*;
import java.lang.reflect.Field;
import java.util.*;

import org.apache.poi.hssf.usermodel.*;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.pmis.common.excel.core.config.ExcelConfig;
import com.njydsz.pmis.common.excel.core.context.AnalysisContext;
import com.njydsz.pmis.common.excel.core.listener.ReadListener;
import com.njydsz.pmis.common.excel.core.metadata.ReadMetadata;
import com.njydsz.pmis.common.excel.core.reader.ColumnMetadata;
import com.njydsz.pmis.common.excel.core.reader.HeaderAnalyzer;
import com.njydsz.pmis.common.excel.core.reader.InputSourceDetector;
import com.njydsz.pmis.common.excel.core.reader.RowParser;
import com.njydsz.pmis.common.excel.core.reader.sax.SuperFastExcelReader;
import com.njydsz.pmis.common.excel.support.asm.ASMFieldAccessor;

/**
 * Excel读取。- 核心读取组件
 *
 * <p>负责Excel文件的读取解析工。支持.xls。xlsx两种格式。
 * 采用用户模式(UserMode)进行读取,通过注解实现列与字段的映射关系。/p>
 *
 * <h2>读取流程</h2>
 * <ol>
 *   <li><b>格式识别</b> - 根据文件扩展名或输入流类型选择解析。</li>
 *   <li><b>Sheet定位</b> - 根据sheetName或sheetIndex获取目标Sheet</li>
 *   <li><b>表头解析</b> - 解析表头。建立列索引与字段的映射关。</li>
 *   <li><b>数据读取</b> - 遍历数据。通过反射设置对象属性</li>
 *   <li><b>回调通知</b> - 触发监听器回。通知每行数据的读取结。</li>
 * </ol>
 *
 * <h2>性能优化策略</h2>
 * <ul>
 *   <li>使用LinkedHashMap保持列顺。避免HashMap的无序开销</li>
 *   <li>反射设置时提前调用setAccessible提高访问效率</li>
 *   <li>监听器批量处理减少频繁回调的开销</li>
 *   <li>日期格式缓存避免重复解析</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 示例1: 基本读取
 * ExcelFacade.read("demo.xlsx", User.class)
 *     .sheet("用户数据")
 *     .doRead(new ReadListener<User>() {
 *         @Override
 *         public void onData(AnalysisContext context, User data) {
 *             System.out.println(data.getName());
 *         }
 *     });
 *
 * // 示例2: 使用Lambda简。
 * ExcelFacade.read("demo.xlsx", User.class, (context, user) -> {
 *     // 处理每行数据
 *     saveToDatabase(user);
 * });
 *
 * // 示例3: 读取所有Sheet
 * for (int i = 0; i < sheetCount; i++) {
 *     ExcelFacade.read("demo.xlsx")
 *         .sheet(i)
 *         .doRead(listener);
 * }
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @email pmis-dev@njydsz.com
 * @version 1.0.0
 * @see ExcelFacade
 * @see ReadListener
 * @see ReadMetadata
 * @see AnalysisContext
 */
public class ExcelReader {

    /** 日志记录数*/
    private static final Logger log = LoggerFactory.getLogger(ExcelReader.class);

    /** 读取配置元数。包含文件路径、映射类型等配置信息 */
    private final ReadMetadata metadata;

    /** 分析上下。用于在监听器回调中传递读取状态如当前行。 */
    private final AnalysisContext context;

    /** 已注册的监听器列。支持多个监听器链式调用*/
    private final List<ReadListener<?>> listeners;

    /** 高性能列元数据缓存 - 预计算的Setter/Type/Format，避免运行时反射 */
    private ColumnMetadata[] columnMetadataArray;

    /** 表头分析。- 负责解析表头行并建立列与字段的映射关。*/
    private final HeaderAnalyzer headerAnalyzer;

    /** 行解析器 - 负责解析Excel数据。*/
    private final RowParser rowParser;

    /** 输入源检测器 - 负责检测输入源类型和格。*/
    private final InputSourceDetector inputSourceDetector;

    /** 批量读取大小 */
    private int batchSize = 0;

    /** 批量数据缓冲。*/
    private List<Object> batchBuffer;

    /**
     * 构造函数- 根据元数据创建读取器
     *
     * @param metadata 读取配置元数。包含文件路径、映射类型、Sheet信息。
     */
    public ExcelReader(ReadMetadata metadata) {
        this.metadata = metadata;
        this.context = new AnalysisContext(metadata);
        this.listeners = new ArrayList<>();
        this.headerAnalyzer = new HeaderAnalyzer(metadata);
        this.rowParser = new RowParser(metadata, context);
        this.inputSourceDetector = new InputSourceDetector(metadata);
    }

    // ==================== Sheet选择配置 ====================

    /**
     * 使用默认配置读取
     *
     * <p>默认读取第一个Sheet(pageIndex=0),表头行号。(。。</p>
     *
     * @return 当前读取器实体支持链式调用
     */
    public ExcelReader sheet() {
        return this;
    }

    /**
     * 指定要读取的Sheet名称
     *
     * <p>根据Sheet名称精确定位要读取的Sheet页。
     * 如果找不到对应名称的Sheet,会抛出异常。</p>
     *
     * @param sheetName Sheet名称(区分大小。
     * @return 当前读取器实体
     * @throws IllegalArgumentException 当Sheet不存在时
     */
    public ExcelReader sheet(String sheetName) {
        metadata.setSheetName(sheetName);
        return this;
    }

    /**
     * 指定要读取的Sheet序号
     *
     * <p>Sheet序号从1开始0表示第一个Sheet。
     * 如果序号超出范围,会读取最后一个Sheet。/p>
     *
     * @param sheetNo Sheet序号(从1开始
     * @return 当前读取器实体
     */
    public ExcelReader sheet(int sheetNo) {
        metadata.setSheetIndex(sheetNo);
        return this;
    }

    // ==================== 读取参数配置 ====================

    /**
     * 指定表头行号
     *
     * <p>表头行用于建立Excel列与Java字段的映射关系。
     * 默认表头行号。(。。因为第一行是index=0)。/p>
     *
     * @param headRowNumber 表头行号(从1开始计。
     * @return 当前读取器实体
     */
    public ExcelReader headRowNumber(int headRowNumber) {
        metadata.setHeadRowNumber(headRowNumber);
        return this;
    }

    /**
     * 注册数据读取监听。
     *
     * <p>每读取一行数据会触发监听器的onData方法。
     * 可以注册多个监听。按注册顺序依次调用。/p>
     *
     * <p>监听器通常用于:
     * <ul>
     *   <li>数据持久。如写入数据库)</li>
     *   <li>数据验证</li>
     *   <li>进度展示</li>
     * </ul>
     *
     * @param listener 数据读取监听。不能为null
     * @return 当前读取器实体
     */
    public ExcelReader registerReadListener(ReadListener<?> listener) {
        this.listeners.add(listener);
        return this;
    }

    /**
     * 设置Sheet密码保护
     *
     * <p>如果Excel文件有密码保。使用此方法提供密码进行解密。
     * 注意:此方法仅适用于有密码保护的Sheet。/p>
     *
     * @param password Sheet保护密码
     * @return 当前读取器实体
     */
    public ExcelReader password(String password) {
        metadata.setPassword(password);
        return this;
    }

    /**
     * 设置跳过空行
     *
     * <p>设置为true。读取过程中会自动跳过完全为空的行。
     * 默认不跳过空行。/p>
     *
     * <h3>使用示例</h3>
     * <pre>{@code
     * ExcelFacade.read("data.xlsx", User.class)
     *     .skipEmptyRows()
     *     .doRead(listener);
     * }</pre>
     *
     * @return 当前读取器实体
     */
    public ExcelReader skipEmptyRows() {
        metadata.setSkipEmptyRows(true);
        return this;
    }

    /**
     * 设置跳过空行(带参数版。
     *
     * @param skip 是否跳过空行
     * @return 当前读取器实体
     */
    public ExcelReader skipEmptyRows(boolean skip) {
        metadata.setSkipEmptyRows(skip);
        return this;
    }

    /**
     * 设置校验列数
     *
     * <p>设置为true。每行数据的列数必须与表头列数一致。
     * 如果不一致会在日志中输出警告。默认不校验。/p>
     *
     * @param expectedColumnCount 期望的列。
     * @return 当前读取器实体
     */
    public ExcelReader checkColumnCount(int expectedColumnCount) {
        metadata.setCheckColumnCount(true);
        metadata.setExpectedColumnCount(expectedColumnCount);
        return this;
    }

    public ExcelReader maxRows(int maxRows) {
        metadata.setMaxRows(maxRows);
        return this;
    }

    /**
     * 设置日期格式
     *
     * <p>用于解析Excel中的日期类型单元格。
     * 支持的格式如:"yyyy-MM-dd"。yyyy/MM/dd HH:mm:ss"等。/p>
     *
     * @param dateFormat 日期格式字符。
     * @return 当前读取器实体
     */
    public ExcelReader dateFormat(String dateFormat) {
        metadata.setDateFormat(dateFormat);
        return this;
    }

    /**
     * 使用1904日期窗口
     *
     * <p>某些Mac版Excel使用1904日期窗口,与Windows。900窗口有差异。
     * 如果读取的日期明显偏大或偏小,尝试调用此方法进行修正则/p>
     *
     * @return 当前读取器实体
     */
    public ExcelReader use1904Windowing() {
        ExcelConfig.getInstance().setUse1904Windowing(true);
        return this;
    }

    /**
     * 强制使用输入流模。
     *
     * <p>某些特殊场景下需要强制从输入流读取而不是文件路。
     * 例如需要先下载文件再解析的场景。/p>
     *
     * @return 当前读取器实体
     */
    public ExcelReader mandatoryUseInputStream() {
        metadata.setMandatoryUseInputStream(true);
        return this;
    }

    /**
     * 设置批量读取大小
     *
     * <p>每读取指定数量的行后，触发一次onBatchData回调用
     * 适合需要批量入库的场景，减少数据库交互次数。/p>
     *
     * @param batchSize 批量大小
     * @return 当前读取器实体
     */
    public ExcelReader batchSize(int batchSize) {
        this.batchSize = batchSize;
        return this;
    }

    // ==================== 列过滤配。====================

    /**
     * 排除指定字段
     *
     * <p>排除后这些字段不会参与Excel读取,即便是实体类中定义了映射。/p>
     *
     * @param excludeColumnFiledNames 要排除的字段名集。
     * @return 当前读取器实体
     */
    public ExcelReader excludeColumnFiledNames(Set<String> excludeColumnFiledNames) {
        metadata.setExcludeColumnFiledNames(excludeColumnFiledNames);
        return this;
    }

    /**
     * 排除指定字段
     *
     * @param excludeColumnFiledNames 要排除的字段名数。
     * @return 当前读取器实体
     */
    public ExcelReader excludeColumnFiledNames(String... excludeColumnFiledNames) {
        Set<String> set = new HashSet<>(Arrays.asList(excludeColumnFiledNames));
        return excludeColumnFiledNames(set);
    }

    /**
     * 只包含指定字。
     *
     * <p>设置后只有指定的字段会被读取,其他字段会被忽略。/p>
     *
     * @param includeColumnFiledNames 要包含的字段名集。
     * @return 当前读取器实体
     */
    public ExcelReader includeColumnFiledNames(Set<String> includeColumnFiledNames) {
        metadata.setIncludeColumnFiledNames(includeColumnFiledNames);
        return this;
    }

    /**
     * 只包含指定字。
     *
     * @param includeColumnFiledNames 要包含的字段名数。
     * @return 当前读取器实体
     */
    public ExcelReader includeColumnFiledNames(String... includeColumnFiledNames) {
        Set<String> set = new HashSet<>(Arrays.asList(includeColumnFiledNames));
        return includeColumnFiledNames(set);
    }

    // ==================== 核心读取方法 ====================

    /**
     * 执行读取(无监听器版本)
     *
     * <p>适用于不需要逐行处理的场。如只需要获取行数等简单操作。/p>
     */
    public void doRead() {
        doRead(null);
    }

    /**
     * 执行读取(带监听器版本)
     *
     * <p>核心读取方法,会根据文件类型自动选择XLS或XLSX解析器。
     * 读取过程中会触发监听器的回调方法。/p>
     *
     * <p><b>。大文件风险警告：</b>SuperFastExcelReader 虽然性能优异，但内部仍会。
     * Sheet XML 数据一次性加载到内存中解析。对于超大文件（行数超过10万行或文件超。0MB），
     * 可能存在 OOM 风险。建议大文件场景优先使用 {@link ReadListener} 流式读取方式。
     * 并通过 {@link #maxRows(int)} 设置最大读取行数以防止内存溢出。/p>
     *
     * <p>执行流程:
     * <ol>
     *   <li>调用所有监听器的onStart</li>
     *   <li>根据文件类型选择解析器并读取</li>
     *   <li>每读取一行调用监听器的onData</li>
     *   <li>读取完成后调用所有监听器的onEnd</li>
     * </ol>
     *
     * @param listener 数据监听。可为null(使用前请先调用registerReadListener)
     * @param <T> 泛型参数,表示映射的数据类型
     * @throws RuntimeException 读取过程中发生错误时抛出
     */
    public <T> void doRead(ReadListener<T> listener) {
        try {
            if (listener != null) {
                listeners.add(listener);
            }
            notifyStart();

            String filePath = metadata.getFilePath();
            boolean isXlsx = filePath != null && filePath.toLowerCase().endsWith(".xlsx");

            if (!isXlsx && filePath == null && metadata.getFile() != null) {
                String fileName = metadata.getFile().getName();
                isXlsx = fileName != null && fileName.toLowerCase().endsWith(".xlsx");
            }

            if (!isXlsx && metadata.getInputStream() != null && filePath == null) {
                isXlsx = inputSourceDetector.detectXlsxFormat(metadata.getInputStream());
            }

            if (filePath != null) {
                File file = new File(filePath);
                long fileSizeMB = file.length() / (1024 * 1024);
                int maxFileSizeMB = ExcelConfig.getInstance().getMaxReadFileSizeMB();
                if (fileSizeMB > maxFileSizeMB) {
                    throw new IllegalArgumentException(
                        "Excel文件过大: " + fileSizeMB + "MB > 最大限。 " + maxFileSizeMB + "MB");
                }
            }

            if (isXlsx && metadata.getClazz() != null) {
                int thresholdMB = ExcelConfig.getInstance().getStreamingParseThresholdMB();
                long fileSizeMB = 0;
                if (filePath != null) {
                    File file = new File(filePath);
                    fileSizeMB = file.length() / (1024 * 1024);
                }

                if (fileSizeMB >= thresholdMB || ExcelConfig.getInstance().isUseFastReader()) {
                    try (FileInputStream fis = new FileInputStream(
                            filePath != null ? filePath : metadata.getFile().getAbsolutePath())) {
                        SuperFastExcelReader superFastReader = new SuperFastExcelReader();
                        superFastReader.setColumnMetadataArray(columnMetadataArray);
                        superFastReader.setInstantiator(
                            ASMFieldAccessor.getInstantiator(metadata.getClazz()));
                        superFastReader.setContext(context);
                        superFastReader.setListeners(listeners);
                        superFastReader.setHeadRowNumber(metadata.getHeadRowNumber());
                        Integer maxRows = metadata.getMaxRows();
                        if (maxRows != null && maxRows > 0) {
                            superFastReader.setMaxRows(maxRows);
                        }
                        superFastReader.read(fis);
                    }
                    notifyEnd();
                    return;
                }
            }

            if (isXlsx) {
                readXlsx();
            } else {
                readXls();
            }
        } catch (Exception e) {
            log.error("Excel 读取异常", e);
            throw new RuntimeException("Excel 读取异常: " + e.getMessage(), e);
        }
    }

    // ==================== 私有解析方法 ====================

    /**
     * 读取全部数据到列。
     *
     * <p>便捷方法，将所有数据读取到List中返回。
     * 注意：大文件场景下可能导致OOM，建议使用doRead + ReadListener流式处理。/p>
     *
     * @param <T> 数据类型
     * @return 数据列表
     */
    public <T> List<T> doReadAll() {
        List<T> result = new ArrayList<>();
        doRead(new ReadListener<T>() {
            @Override
            public void onStart(AnalysisContext context) {}
            @Override
            public void onData(AnalysisContext context, T data) {
                result.add(data);
            }
            @Override
            public void onEnd(AnalysisContext context) {}
        });
        return result;
    }

    // ==================== 私有解析方法 ====================

    /**
     * 读取XLSX格式(Excel 2007+)
     *
     * <p>使用 POI 用户模式进行解析。
     * 对于大数据量场景，建议使。SuperFastExcelReader。/p>
     *
     * @throws IOException 文件读取异常
     */
    private void readXlsx() throws IOException {
        InputStream is = inputSourceDetector.getInputStream();
        if (is == null) {
            is = new FileInputStream(metadata.getFilePath());
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = getSheet(workbook);
            if (sheet == null) {
                throw new IllegalArgumentException("Sheet不存在");
            }

            parseSheet(sheet);
        }
    }

    /**
     * 读取XLS格式(Excel 97-2003)
     *
     * <p>使用Apache POI的HSSFWorkbook进行解析。
     * 通过POIFSFileSystem包装输入流以支持加密文档的读取。/p>
     *
     * @throws IOException 文件读取异常
     */
    private void readXls() throws IOException {
        InputStream is = inputSourceDetector.getInputStream();
        if (is == null) {
            is = new FileInputStream(metadata.getFilePath());
        }

        try (POIFSFileSystem fs = new POIFSFileSystem(is);
             HSSFWorkbook workbook = new HSSFWorkbook(fs)) {
            Sheet sheet = getSheet(workbook);
            if (sheet == null) {
                throw new IllegalArgumentException("Sheet不存在");
            }

            parseSheet(sheet);
        }
    }

    /**
     * 根据配置获取目标Sheet
     *
     * <p>优先。 sheetName > sheetIndex > 默认第一个Sheet(pageIndex=0)</p>
     *
     * @param workbook 工作簿对。
     * @return Sheet对象,若不存在返回null
     */
    private Sheet getSheet(Workbook workbook) {
        String sheetName = metadata.getSheetName();
        Integer sheetIndex = metadata.getSheetIndex();

        if (sheetName != null && !sheetName.isEmpty()) {
            return workbook.getSheet(sheetName);
        } else if (sheetIndex != null && sheetIndex >= 0) {
            int sheetCount = workbook.getNumberOfSheets();
            if (sheetIndex < sheetCount) {
                return workbook.getSheetAt(sheetIndex);
            }
        }
        return workbook.getSheetAt(0);
    }

    /**
     * 解析Sheet。
     *
     * <p>主要解析流程:
     * <ol>
     *   <li>验证表头行存。</li>
     *   <li>建立列与字段的映射关。</li>
     *   <li>逐行解析并触发监听器</li>
     * </ol>
     *
     * @param sheet 要解析的Sheet。
     * @throws IOException IO异常
     */
    
    private void parseSheet(Sheet sheet) throws IOException {
        int headRowNumber = metadata.getHeadRowNumber();
        Row headRow = sheet.getRow(headRowNumber);

        if (headRow == null) {
            throw new IllegalArgumentException("Excel文件为空或没有表头行");
        }

        List<String> headers = new ArrayList<>();
        Map<Integer, Field> fieldMap = new HashMap<>();

        if (metadata.getClazz() != null) {
            columnMetadataArray = headerAnalyzer.analyzeClassMetadata(headRow, headers, fieldMap);
        } else {
            headerAnalyzer.analyzeHeaders(headRow, headers);
        }

        int lastRowNum = sheet.getLastRowNum();
        int startRow = headRowNumber + 1;

        boolean skipEmptyRows = Boolean.TRUE.equals(metadata.getSkipEmptyRows());
        boolean checkColumnCount = Boolean.TRUE.equals(metadata.getCheckColumnCount());
        Integer expectedColumnCount = metadata.getExpectedColumnCount();
        boolean hasListeners = !listeners.isEmpty();
        int listenerCount = listeners.size();
        int[] checkColumnIndices = rowParser.buildCheckColumnIndices(columnMetadataArray);
        Integer maxRows = metadata.getMaxRows();
        int dataRowCount = 0;

        for (int rowIndex = startRow; rowIndex <= lastRowNum; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }

            if (skipEmptyRows && rowParser.isRowEmptyFast(row, checkColumnIndices)) {
                continue;
            }

            if (maxRows != null && maxRows > 0 && dataRowCount >= maxRows) {
                break;
            }

            if (checkColumnCount) {
                int actualCount = row.getLastCellNum();
                if (expectedColumnCount != null && actualCount != expectedColumnCount.intValue()) {
                    log.warn("列数不匹。 期望={}, 实际={}, 行号={}",
                        expectedColumnCount, actualCount, rowIndex);
                }
            }

            context.setCurrentRow(rowIndex);
            Object data = rowParser.parseRow(row, headers, fieldMap, columnMetadataArray);

            if (data != null && hasListeners) {
                context.incrementRow();
                if (batchSize > 0) {
                    if (batchBuffer == null) {
                        batchBuffer = new ArrayList<>(batchSize);
                    }
                    batchBuffer.add(data);
                    if (batchBuffer.size() >= batchSize) {
                        for (int i = 0; i < listenerCount; i++) {
                            ((ReadListener<Object>) listeners.get(i)).onBatchData(context, batchBuffer);
                        }
                        batchBuffer.clear();
                    }
                } else {
                    for (int i = 0; i < listenerCount; i++) {
                        ((ReadListener<Object>) listeners.get(i)).onData(context, data);
                    }
                }
            }
        }
    }

    // ==================== 监听器通知方法 ====================

    /**
     * 通知所有监听器读取开始
     *
     * <p>在开始解析之前调用让监听器进行初始化操。/p>
     */
    private void notifyStart() {
        for (ReadListener<?> listener : listeners) {
            listener.onStart(context);
        }
    }

    /**
     * 通知所有监听器读取结束
     *
     * <p>读取完成后调用无论是否发生异常。。
     * 用于资源清理和统计汇总。/p>
     */
    private void notifyEnd() {
        // Flush remaining batch data
        if (batchBuffer != null && !batchBuffer.isEmpty()) {
            for (ReadListener<?> listener : listeners) {
                ((ReadListener<Object>) listener).onBatchData(context, batchBuffer);
            }
            batchBuffer.clear();
        }
        for (ReadListener<?> listener : listeners) {
            listener.onEnd(context);
        }
    }
}
