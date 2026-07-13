package com.njydsz.pmis.common.excel.core;

import com.njydsz.pmis.common.excel.annotation.ExcelIgnore;
import com.njydsz.pmis.common.excel.annotation.ExcelProperty;
import com.njydsz.pmis.common.excel.annotation.ExcelSheet;
import com.njydsz.pmis.common.excel.annotation.ExcelStyle;
import com.njydsz.pmis.common.excel.core.config.ExcelConfig;
import com.njydsz.pmis.common.excel.core.context.WriteContext;
import com.njydsz.pmis.common.excel.core.writer.SuperFastExcelWriter;
import com.njydsz.pmis.common.excel.core.writer.UltraFastCellWriter;
import com.njydsz.pmis.common.excel.core.writer.PrecomputedColumnProperties;
import com.njydsz.pmis.common.excel.core.writer.WorkbookFactory;
import com.njydsz.pmis.common.excel.core.writer.ValueFormatter;
import com.njydsz.pmis.common.excel.core.writer.StyleManager;
import com.njydsz.pmis.common.excel.support.cache.ReflectCache;
import com.njydsz.pmis.common.excel.support.asm.ASMFieldAccessor;
import com.njydsz.pmis.common.excel.core.metadata.WriteMetadata;
import com.njydsz.pmis.common.excel.core.metadata.WriteMetadata.WriteHeaderProperty;
import com.njydsz.pmis.common.excel.core.metadata.MetadataCache;
import com.njydsz.pmis.common.excel.core.metadata.MetadataCache.CachedWriteMetadata;
import com.njydsz.pmis.common.excel.core.metadata.MetadataCache.CachedProperty;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.njydsz.pmis.common.excel.exception.ExcelWriteException;

import java.io.*;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
/**
 * Excel写入器 - 核心写入组件
 *
 * <p>负责Excel文件的数据写入工作,支持.xls和.xlsx两种格式输出。
 * 采用SXSSF(Streaming Usermodel)模式实现低内存写入,适合大数据量场景。</p>
 *
 * <h3>写入流程</h3>
 * <ol>
 *   <li>根据输出目标初始化对应的工作簿类型</li>
 *   <li>解析映射类的注解信息,构建表头属性列表</li>
 *   <li>写入表头行并设置样式</li>
 *   <li>遍历数据集合,逐行写入并触发监听器</li>
 *   <li>刷写缓冲并释放资源</li>
 * </ol>
 *
 * <h3>性能优化策略</h3>
 * <ul>
 *   <li>SXSSFWorkbook使用临时文件存储,显著降低内存占用</li>
 *   <li>启用GZIP压缩临时文件,减少磁盘IO</li>
 *   <li>写入时自动设置setAccessible,提升反射效率</li>
 *   <li>列宽自适应计算,支持最小宽度限制</li>
 * </ul>
 *
 * @see ExcelFacade
 * @see WriteMetadata
 * @see WriteHandler
 */
public class ExcelWriter {

    private static final Logger log = LoggerFactory.getLogger(ExcelWriter.class);

    /** 写入配置元数据 */
    private final WriteMetadata metadata;

    /** 写入上下文,记录当前写入状态 */
    private final WriteContext context;

    /** 已注册的写入处理器列表 */
    private final List<Object> handlers;

    /** Apache POI工作簿对象 */
    private Workbook workbook;

    /** 当前写入的Sheet页 */
    private Sheet sheet;

    /** 当前写入行号 */
    private int currentRowIndex;

    /** 是否追加写入模式 */
    private boolean append;

    /** 样式管理器 - 管理单元格样式缓存 */
    private final StyleManager styleManager;

    /** 工作簿工厂 - 负责创建和初始化Workbook */
    private final WorkbookFactory workbookFactory;

    /** 值格式化器 - 负责单元格值设置和日期格式化 */
    private ValueFormatter valueFormatter;

    /** 超高速单元格写入器 - 零拷贝路径 */
    private UltraFastCellWriter ultraFastCellWriter;

    /** 预计算列属性 - 避免运行时重复计算 */
    private PrecomputedColumnProperties precomputedProps;

    /**
     * 构造方法
     *
     * @param metadata 写入配置元数据,包含目标路径、映射类型等信息
     */
    public ExcelWriter(WriteMetadata metadata) {
        this.metadata = metadata;
        this.context = new WriteContext(metadata);
        this.handlers = new ArrayList<>();
        this.currentRowIndex = 0;
        this.append = false;
        this.styleManager = new StyleManager(512);
        this.workbookFactory = new WorkbookFactory();
        this.valueFormatter = new ValueFormatter(metadata.getAutomaticTrim() != null ? metadata.getAutomaticTrim() : true);
    }

    // ==================== 链式配置方法 ====================

    /**
     * 使用默认配置(创建名为"sheet1"的Sheet)
     *
     * @return 当前写入器实例,支持链式调用
     */
    public ExcelWriter sheet() {
        return sheet("sheet1");
    }

    /**
     * 指定Sheet名称
     *
     * @param sheetName Sheet名称
     * @return 当前写入器实例
     */
    public ExcelWriter sheet(String sheetName) {
        metadata.setSheetName(sheetName);
        return this;
    }

    /**
     * 指定Sheet序号(创建多个Sheet时使用)
     *
     * @param sheetNo Sheet序号
     * @return 当前写入器实例
     */
    public ExcelWriter sheetNo(int sheetNo) {
        metadata.setSheetNo(sheetNo);
        return this;
    }

    /**
     * 创建新Sheet并返回新的写入器(用于多Sheet写入)
     *
     * <p>在多Sheet写入场景中,第一个Sheet使用sheet()方法指定名称,
     * 后续Sheet使用newSheet()方法创建。</p>
     *
     * <h3>使用示例</h3>
     * <pre>{@code
     * ExcelWriter writer = ExcelFacade.write("output.xlsx", User.class);
     * writer.sheet("用户信息").doWrite(userList);  // 第一个Sheet
     *
     * writer.newSheet("部门信息").doWrite(deptList);  // 第二个Sheet
     * writer.newSheet("角色信息").doWrite(roleList);  // 第三个Sheet
     * }</pre>
     *
     * @param sheetName 新Sheet的名称
     * @return 新的ExcelWriter实例,关联到新创建的Sheet
     */
    public ExcelWriter newSheet(String sheetName) {
        WriteMetadata newMetadata = copyMetadata();
        newMetadata.setSheetName(sheetName);
        newMetadata.setSheetNo(workbook != null ? workbook.getNumberOfSheets() : 0);
        newMetadata.setHeadRowNumber(1);

        ExcelWriter newWriter = new ExcelWriter(newMetadata);
        if (this.workbook != null) {
            newWriter.workbook = this.workbook;
            newWriter.setMultiSheetWriting(true);
            Sheet newSheet = this.workbook.createSheet(sheetName);
            newWriter.sheet = newSheet;
            newWriter.context.setSheet(newSheet);
            newWriter.styleManager.setStyleHandler(this.styleManager.getStyleHandler());
        }
        return newWriter;
    }

    /**
     * 复制当前元数据
     *
     * @return 新的WriteMetadata副本
     */
    private WriteMetadata copyMetadata() {
        WriteMetadata newMetadata = new WriteMetadata();
        newMetadata.setClazz(metadata.getClazz());
        newMetadata.setFilePath(metadata.getFilePath());
        newMetadata.setFile(metadata.getFile());
        newMetadata.setOutputStream(metadata.getOutputStream());
        newMetadata.setHeadRowNumber(metadata.getHeadRowNumber());
        newMetadata.setDateFormat(metadata.getDateFormat());
        newMetadata.setNumberFormat(metadata.getNumberFormat());
        newMetadata.setAutomaticTrim(metadata.getAutomaticTrim());
        newMetadata.setPassword(metadata.getPassword());
        newMetadata.setExcludeColumnFiledNames(metadata.getExcludeColumnFiledNames());
        newMetadata.setIncludeColumnFiledNames(metadata.getIncludeColumnFiledNames());
        newMetadata.setFreezePaneRow(metadata.getFreezePaneRow());
        newMetadata.setFreezePaneCol(metadata.getFreezePaneCol());
        newMetadata.setAutoColumnWidth(metadata.getAutoColumnWidth());
        newMetadata.setMergedRegions(metadata.getMergedRegions());
        return newMetadata;
    }

    /**
     * 指定表头行号
     *
     * <p>数据将从此行号之后开始写入</p>
     *
     * @param headRowNumber 表头行号(从0开始计数)
     * @return 当前写入器实例
     */
    public ExcelWriter headRowNumber(int headRowNumber) {
        metadata.setHeadRowNumber(headRowNumber);
        return this;
    }

    /**
     * 使用1904日期窗口
     *
     * @return 当前写入器实例
     */
    public ExcelWriter use1904Windowing() {
        ExcelConfig.getInstance().setUse1904Windowing(true);
        return this;
    }

    /**
     * 设置日期格式
     *
     * <p>用于格式化Date类型字段的输出</p>
     *
     * @param dateFormat 日期格式,如"yyyy-MM-dd"
     * @return 当前写入器实例
     */
    public ExcelWriter dateFormat(String dateFormat) {
        metadata.setDateFormat(dateFormat);
        return this;
    }

    /**
     * 设置数字格式
     *
     * @param numberFormat 数字格式,如"#,##0.00"
     * @return 当前写入器实例
     */
    public ExcelWriter numberFormat(String numberFormat) {
        metadata.setNumberFormat(numberFormat);
        return this;
    }

    /**
     * 设置Sheet保护密码
     *
     * <p>设置后Sheet将处于保护状态,需要密码才能编辑</p>
     *
     * @param password 保护密码
     * @return 当前写入器实例
     */
    public ExcelWriter password(String password) {
        metadata.setPassword(password);
        return this;
    }

    /**
     * 设置冻结窗格
     *
     * <p>用于固定表头或首列,方便查看大数据量时的滚动浏览。
     * 例如 freezePane(1, 0) 冻结首行, freezePane(0, 1) 冻结首列</p>
     *
     * <h3>使用示例</h3>
     * <pre>{@code
     * ExcelFacade.write("output.xlsx", User.class)
     *     .sheet("用户")
     *     .freezePane(1, 0)  // 冻结首行
     *     .doWrite(userList);
     * }</pre>
     *
     * @param row 冻结的行数(从首行开始),0表示不冻结行
     * @param col 冻结的列数(从首列开始),0表示不冻结列
     * @return 当前写入器实例
     */
    public ExcelWriter freezePane(int row, int col) {
        metadata.setFreezePaneRow(row);
        metadata.setFreezePaneCol(col);
        return this;
    }

    /**
     * 设置自动调整列宽
     *
     * <p>设置为true时,在写入完成后自动根据内容调整列宽。
     * 注意:此功能在 SXSSF 模式下不生效</p>
     *
     * <h3>使用示例</h3>
     * <pre>{@code
     * ExcelFacade.write("output.xlsx", User.class)
     *     .sheet("用户")
     *     .autoColumnWidth(true)
     *     .doWrite(userList);
     * }</pre>
     *
     * @param autoColumnWidth true表示自动调整列宽
     * @return 当前写入器实例
     */
    public ExcelWriter autoColumnWidth(boolean autoColumnWidth) {
        metadata.setAutoColumnWidth(autoColumnWidth);
        return this;
    }

    /**
     * 注册写入处理器
     *
     * @param handler 写入处理器
     * @return 当前写入器实例
     */
    public ExcelWriter registerWriteHandler(Object handler) {
        this.handlers.add(handler);
        return this;
    }

    /**
     * 设置是否自动去除字符串首尾空格
     *
     * @param automaticTrim true启用自动去空格,默认true
     * @return 当前写入器实例
     */
    public ExcelWriter automaticTrim(boolean automaticTrim) {
        metadata.setAutomaticTrim(automaticTrim);
        return this;
    }

    /**
     * 追加写入模式
     *
     * <p>启用追加模式后,写入数据时会从已有数据的下一行开始写入,
     * 而不是覆盖原有数据。适用于需要分批写入数据的场景。</p>
     *
     * <h3>使用示例</h3>
     * <pre>{@code
     * // 第一次写入
     * ExcelFacade.write("output.xlsx", User.class)
     *     .sheet("用户数据")
     *     .doWrite(firstBatch);
     *
     * // 追加第二次写入
     * ExcelFacade.write("output.xlsx", User.class)
     *     .sheet("用户数据")
     *     .append()
     *     .doWrite(secondBatch);
     * }</pre>
     *
     * @return 当前写入器实例
     */
    public ExcelWriter append() {
        this.append = true;
        return this;
    }

    // ==================== 列过滤配置 ====================

    /**
     * 排除指定字段
     *
     * <p>排除后这些字段不会参与Excel写入</p>
     *
     * @param excludeColumnFiledNames 要排除的字段名集合
     * @return 当前写入器实例
     */
    public ExcelWriter excludeColumnFiledNames(Set<String> excludeColumnFiledNames) {
        metadata.setExcludeColumnFiledNames(excludeColumnFiledNames);
        return this;
    }

    /**
     * 排除指定字段
     *
     * @param excludeColumnFiledNames 要排除的字段名数组
     * @return 当前写入器实例
     */
    public ExcelWriter excludeColumnFiledNames(String... excludeColumnFiledNames) {
        Set<String> set = new HashSet<>(Arrays.asList(excludeColumnFiledNames));
        return excludeColumnFiledNames(set);
    }

    /**
     * 只包含指定字段
     *
     * @param includeColumnFiledNames 要包含的字段名集合
     * @return 当前写入器实例
     */
    public ExcelWriter includeColumnFiledNames(Set<String> includeColumnFiledNames) {
        metadata.setIncludeColumnFiledNames(includeColumnFiledNames);
        return this;
    }

    /**
     * 只包含指定字段
     *
     * @param includeColumnFiledNames 要包含的字段名数组
     * @return 当前写入器实例
     */
    public ExcelWriter includeColumnFiledNames(String... includeColumnFiledNames) {
        Set<String> set = new HashSet<>(Arrays.asList(includeColumnFiledNames));
        return includeColumnFiledNames(set);
    }

    // ==================== 核心写入方法 ====================

    /**
     * 执行写入(写入到默认Sheet)
     *
     * @param data 要写入的数据,支持List、数组或单个对象
     */
    public void doWrite(Object data) {
        doWrite(data, 0);
    }

    /**
     * 执行写入(写入到指定Sheet序号)
     *
     * <p>核心写入方法,会依次执行:
     * <ol>
     *   <li>初始化工作簿</li>
     *   <li>解析类注解构建表头</li>
     *   <li>写入表头和数据</li>
     *   <li>刷写缓冲并释放资源</li>
     * </ol>
     *
     * @param data 要写入的数据
     * @param sheetNo Sheet序号
     * @throws RuntimeException 写入过程中发生错误时抛出
     */
    public void doWrite(Object data, int sheetNo) {
        try {
            if (metadata.getDataSize() == null && data instanceof List) {
                metadata.setDataSize(((List<?>) data).size());
            }

            boolean useFastWriter = ExcelConfig.getInstance().isUseFastWriter();
            boolean isXlsx = true;
            if (metadata.getFilePath() != null) {
                isXlsx = !metadata.getFilePath().toLowerCase().endsWith(".xls");
            }

            boolean isDefaultSheetName = metadata.getSheetName() == null 
                    || "sheet1".equalsIgnoreCase(metadata.getSheetName());
            
            if (useFastWriter && isXlsx && !append && isDefaultSheetName
                    && metadata.getClazz() != null && !isMultiSheetWriting) {
                SuperFastExcelWriter fastWriter = new SuperFastExcelWriter(metadata);
                fastWriter.doWrite(data);
                return;
            }

            initWorkbook();

            Sheet currentSheet = workbook.getSheetAt(sheetNo);
            this.sheet = currentSheet;
            context.setSheet(currentSheet);

            if (isMultiSheetWriting) {
                currentRowIndex = metadata.getHeadRowNumber();
            } else if (append && currentRowIndex <= 0) {
                currentRowIndex = workbookFactory.findLastRowIndex(sheet, metadata) + 1;
            } else if (!append) {
                currentRowIndex = metadata.getHeadRowNumber();
            }

            List<WriteHeaderProperty> headProperties = analyzeClass();

            if (!append) {
                writeHead(headProperties);
            }

            writeData(data);

            applySheetSettings();

            if (!isMultiSheetWriting) {
                finish();
                markWriteCompleted();
            }

        } catch (Exception e) {
            log.error("Excel写入异常, 当前行号: {}", currentRowIndex, e);
            throw ExcelWriteException.dataWriteFailed(
                currentRowIndex, null, null, e);
        }
    }

    /** 是否正在多Sheet写入流程中 */
    private boolean isMultiSheetWriting = false;

    /** 是否已经完成写入(避免重复finish) */
    private boolean writeCompleted = false;

    /**
     * 设置多Sheet写入模式
     *
     * <p>在多Sheet写入时调用,避免快速写入器覆盖已有内容</p>
     *
     * @param multiSheet 是否多Sheet写入
     * @return 当前写入器实例
     */
    public ExcelWriter setMultiSheetWriting(boolean multiSheet) {
        this.isMultiSheetWriting = multiSheet;
        return this;
    }

    /**
     * 检查是否可以进行写入
     *
     * @return true 如果可以写入，false 如果已经完成过写入
     */
    @SuppressWarnings("unused")
    private boolean canWrite() {
        return !writeCompleted;
    }

    /**
     * 标记写入完成
 * @author ydsz-pmis-team
 * @email pmis-dev@njydsz.com
 * @version 1.0.0
     */
    private void markWriteCompleted() {
        this.writeCompleted = true;
    }

    /**
     * 应用Sheet设置
     *
     * <p>根据metadata中的配置应用以下设置:
     * <ul>
     *   <li>冻结行/列 (freeze pane)</li>
     *   <li>合并单元格区域</li>
     *   <li>列宽自适应</li>
     * </ul>
     */
    private void applySheetSettings() {
        Integer freezeRow = metadata.getFreezePaneRow();
        Integer freezeCol = metadata.getFreezePaneCol();
        if (freezeRow != null || freezeCol != null) {
            int row = freezeRow != null ? freezeRow : 0;
            int col = freezeCol != null ? freezeCol : 0;
            sheet.createFreezePane(col, row);
        }

        List<int[]> mergedRegions = metadata.getMergedRegions();
        if (mergedRegions != null && !mergedRegions.isEmpty()) {
            for (int[] region : mergedRegions) {
                sheet.addMergedRegion(new CellRangeAddress(
                    region[0], region[1], region[2], region[3]));
            }
        }

        if (metadata.getAutoColumnWidth() != null && metadata.getAutoColumnWidth()) {
            if (!(workbook instanceof SXSSFWorkbook)) {
                List<WriteHeaderProperty> headProperties = metadata.getHeadList();
                if (headProperties != null) {
                    for (WriteHeaderProperty property : headProperties) {
                        sheet.autoSizeColumn(property.getColumnIndex());
                    }
                }
            }
        }
    }

    /**
     * 初始化工作簿
     *
     * <p>根据文件扩展名判断格式:
     * <ul>
     *   <li>.xlsx -> SXSSFWorkbook(流式写入,低内存)</li>
     *   <li>.xls -> HSSFWorkbook(传统写入)</li>
     * </ul>
     *
     * <p>追加模式时:
     * <ul>
     *   <li>如果文件存在,则打开已有工作簿</li>
     *   <li>获取或创建目标Sheet</li>
     *   <li>设置起始行为Sheet最后一行+1</li>
     * </ul>
     *
     * @throws IOException 文件创建异常
     */
    private void initWorkbook() throws IOException {
        if (workbook != null) {
            return;
        }

        WorkbookFactory.WorkbookInitResult result = workbookFactory.initWorkbook(metadata, context, null, append);
        this.workbook = result.getWorkbook();
        this.sheet = result.getSheet();
        if (result.getStyleHandler() != null) {
            this.styleManager.setStyleHandler(result.getStyleHandler());
        }
        if (result.getCurrentRowIndex() > 0) {
            this.currentRowIndex = result.getCurrentRowIndex();
        }
    }

    /**
     * 解析类注解信息
     *
     * <p>构建写入属性列表的过程:
     * <ol>
     *   <li>检查类级别@ExcelSheet注解,提取Sheet配置</li>
     *   <li>收集所有带@ExcelProperty的字段</li>
     *   <li>按order属性排序</li>
     *   <li>构建字段名、日期格式、列宽等属性</li>
     * </ol>
     *
     * @return 表头属性列表
     */
    private List<WriteHeaderProperty> analyzeClass() {
        List<WriteHeaderProperty> headProperties = new ArrayList<>();
        if (metadata.getClazz() == null) {
            return headProperties;
        }

        Class<?> clazz = metadata.getClazz();
        Field[] fields = ReflectCache.getCachedFields(clazz);

        ExcelSheet sheetAnnotation = clazz.getAnnotation(ExcelSheet.class);
        if (sheetAnnotation != null) {
            if (!sheetAnnotation.name().isEmpty()) {
                metadata.setSheetName(sheetAnnotation.name());
            }
            if (sheetAnnotation.headRowNumber() > 0) {
                metadata.setHeadRowNumber(sheetAnnotation.headRowNumber());
            }
            if (!sheetAnnotation.dateFormat().isEmpty()) {
                metadata.setDateFormat(sheetAnnotation.dateFormat());
            }
            metadata.setFreezePaneRow(sheetAnnotation.freezePane().row());
            metadata.setFreezePaneCol(sheetAnnotation.freezePane().col());
            metadata.setAutoColumnWidth(sheetAnnotation.autoColumnWidth());

            ExcelSheet.MergedRegion[] mergedRegions = sheetAnnotation.mergedRegions();
            if (mergedRegions != null && mergedRegions.length > 0) {
                List<int[]> regionList = new ArrayList<>();
                for (ExcelSheet.MergedRegion region : mergedRegions) {
                    regionList.add(new int[]{
                        region.startRow(), region.endRow(),
                        region.startCol(), region.endCol()
                    });
                }
                metadata.setMergedRegions(regionList);
            }
        }

        List<Field> annotatedFields = new ArrayList<>();
        for (Field field : fields) {
            if (field.isAnnotationPresent(ExcelIgnore.class)) {
                continue;
            }
            if (field.isAnnotationPresent(ExcelProperty.class)) {
                annotatedFields.add(field);
            }
        }

        annotatedFields.sort(Comparator.comparingInt(f -> {
            ExcelProperty ann = f.getAnnotation(ExcelProperty.class);
            return ann.order();
        }));

        Set<String> excludeFields = metadata.getExcludeColumnFiledNames();
        Set<String> includeFields = metadata.getIncludeColumnFiledNames();

        for (Field field : annotatedFields) {
            String fieldName = field.getName();
            if (excludeFields != null && excludeFields.contains(fieldName)) {
                continue;
            }
            if (includeFields != null && !includeFields.isEmpty() && !includeFields.contains(fieldName)) {
                continue;
            }

            ExcelProperty ann = field.getAnnotation(ExcelProperty.class);
            WriteHeaderProperty property = new WriteHeaderProperty();
            property.setField(field);
            field.setAccessible(true);

            ASMFieldAccessor.FieldGetter asmGetter = ASMFieldAccessor.getGetter(clazz, field);
            property.setAsmFieldGetter(asmGetter);

            String name = ann.value();
            if (name == null || name.isEmpty()) {
                name = field.getName();
            }
            property.setName(name);

            if (!ann.dateFormat().isEmpty()) {
                property.setDateFormat(ann.dateFormat());
            } else if (metadata.getDateFormat() != null && !metadata.getDateFormat().isEmpty()) {
                property.setDateFormat(metadata.getDateFormat());
            } else {
                property.setDateFormat(ExcelConfig.getInstance().getDefaultDateFormat());
            }

            if (ann.width() > 0) {
                property.setWidth((short) ann.width());
            }

            ExcelStyle styleAnnotation = field.getAnnotation(ExcelStyle.class);
            if (styleAnnotation != null) {
                property.setStyle(styleAnnotation);
            }

            if (!ann.formula().isEmpty()) {
                property.setFormula(ann.formula());
            }

            headProperties.add(property);
            metadata.addHeadProperty(property);
        }

        for (int i = 0; i < headProperties.size(); i++) {
            headProperties.get(i).setColumnIndex(i);
        }

        return headProperties;
    }

    /**
     * 写入表头行
     *
     * <p>表头样式特点:
     * <ul>
     *   <li>加粗字体(Calibri, 11pt)</li>
     *   <li>灰色背景填充</li>
     *   <li>水平和垂直居中对齐</li>
     *   <li>双线边框</li>
     *   <li>自动换行</li>
     * </ul>
     *
     * @param headProperties 表头属性列表
     */
    private void writeHead(List<WriteHeaderProperty> headProperties) {
        if (headProperties.isEmpty()) {
            return;
        }

        Row row = sheet.createRow(currentRowIndex++);

        for (WriteHeaderProperty property : headProperties) {
            int colIndex = property.getColumnIndex();
            Cell cell = row.createCell(colIndex);
            cell.setCellValue(property.getName());

            CellStyle style = styleManager.getHeadStyle(property.getStyle());
            cell.setCellStyle(style);

            if (property.getWidth() != null && property.getWidth() > 0) {
                sheet.setColumnWidth(colIndex, property.getWidth() * 256);
            } else if (!(workbook instanceof SXSSFWorkbook)) {
                sheet.autoSizeColumn(colIndex);
                int width = sheet.getColumnWidth(colIndex);
                sheet.setColumnWidth(colIndex, width < 3000 ? 3000 : width);
            } else {
                sheet.setColumnWidth(colIndex, 3000);
            }
        }

        precomputedProps = new PrecomputedColumnProperties(headProperties, styleManager.getStyleHandler());
        ultraFastCellWriter = new UltraFastCellWriter(metadata.getAutomaticTrim());
    }

    /**
     * 写入数据
     *
     * <p>支持多种数据格式:
     * <ul>
     *   <li>List - 遍历列表逐行写入</li>
     *   <li>数组 - 遍历数组逐行写入</li>
     *   <li>单个对象 - 直接写入单行</li>
     * </ul>
     *
     * @param data 要写入的数据
     */
    private void writeData(Object data) {
        if (data == null) {
            return;
        }

        if (data instanceof List) {
            List<?> list = (List<?>) data;
            for (Object item : list) {
                writeRow(item);
            }
        } else if (data instanceof Object[]) {
            Object[] array = (Object[]) data;
            for (Object item : array) {
                writeRow(item);
            }
        } else {
            writeRow(data);
        }
    }

    /**
     * 写入单行数据
     *
     * <p>从数据对象中提取各字段值,写入对应列单元格。
     * 优化: 缓存字段样式,减少重复的样式查找调用。</p>
     *
     * @param rowData 单行数据对象
     */
    private void writeRow(Object rowData) {
        Row row = sheet.createRow(currentRowIndex++);
        context.setCurrentRow(currentRowIndex - 1);

        if (rowData == null) {
            return;
        }

        if (precomputedProps != null && ultraFastCellWriter != null) {
            writeRowUltraFast(row, rowData);
            return;
        }

        List<WriteHeaderProperty> properties = metadata.getHeadList();
        if (properties.isEmpty()) {
            writeMapRow(row, (Map<?, ?>) rowData);
            return;
        }

        int currentRowNum = currentRowIndex;
        for (WriteHeaderProperty property : properties) {
            int colIndex = property.getColumnIndex();
            Cell cell = row.createCell(colIndex);

            ExcelStyle style = property.getStyle();
            if (style != null) {
                CellStyle cellStyle = styleManager.getOrCreateDataStyle(style);
                cell.setCellStyle(cellStyle);
            }

            String formula = property.getFormula();
            if (formula != null && !formula.isEmpty()) {
                String actualFormula = fastReplace(formula, "{row}", String.valueOf(currentRowNum));
                cell.setCellFormula(actualFormula);
            } else {
                try {
                    Object value = property.getAsmFieldGetter().get(rowData);
                    valueFormatter.setCellValueFast(cell, value, property.getDateFormat());
                } catch (Exception e) {
                    log.warn("获取字段值异常", e);
                    cell.setBlank();
                }
            }
        }
    }

    /**
     * 超高速行写入 - 使用预计算属性
     *
     * <p>零分配路径，避免所有运行时判断和查找。</p>
     *
     * @param row Excel行
     * @param rowData 数据对象
     */
    private void writeRowUltraFast(Row row, Object rowData) {
        int columnCount = precomputedProps.getColumnCount();
        int currentRowNum = currentRowIndex;

        for (int i = 0; i < columnCount; i++) {
            Cell cell = row.createCell(i);

            CellStyle style = precomputedProps.getCellStyle(i);
            if (style != null) {
                cell.setCellStyle(style);
            }

            String formula = precomputedProps.getFormulaTemplate(i);
            if (formula != null && !formula.isEmpty()) {
                String actualFormula = fastReplace(formula, "{row}", String.valueOf(currentRowNum));
                cell.setCellFormula(actualFormula);
            } else {
                try {
                    WriteHeaderProperty property = metadata.getHeadList().get(i);
                    Object value = property.getAsmFieldGetter().get(rowData);
                    ultraFastCellWriter.writeFast(cell, value, precomputedProps.getDateFormat(i));
                } catch (Exception e) {
                    log.warn("获取字段值异常", e);
                    cell.setBlank();
                }
            }
        }
    }

    /**
     * 超高速批量写入 - 使用预计算元数据和ASM加速
     *
     * <p>零分配写入循环，适用于大数据量场景。
     * 通过预计算元数据、缓存样式和ASM字节码访问，避免运行时的所有开销。</p>
     *
     * @param dataList 数据列表
     */
    public void writeBatch(List<?> dataList) {
        if (dataList == null || dataList.isEmpty()) {
            return;
        }

        Class<?> clazz = metadata.getClazz();
        if (clazz == null) {
            for (Object data : dataList) {
                writeRow(data);
            }
            return;
        }

        CachedWriteMetadata cached = MetadataCache.getOrCreate(clazz);
        int size = dataList.size();

        for (int i = 0; i < size; i++) {
            Object rowData = dataList.get(i);
            int rowNum = currentRowIndex++;
            Row row = sheet.createRow(rowNum);
            context.setCurrentRow(rowNum);

            if (rowData == null) {
                continue;
            }

            int currentRowNumForFormula = rowNum + 1;
            for (int j = 0; j < cached.fieldCount; j++) {
                CachedProperty prop = cached.properties[j];
                Cell cell = row.createCell(j);

                try {
                    Object value = prop.getValue(rowData);
                    if (prop.formula != null && !prop.formula.isEmpty()) {
                        String actualFormula = fastReplace(prop.formula, "{row}", 
                            String.valueOf(currentRowNumForFormula));
                        cell.setCellFormula(actualFormula);
                    } else {
                        ultraFastCellWriter.writeFast(cell, value, prop.dateFormat);
                    }
                } catch (Exception e) {
                    log.warn("写入第{}行第{}列异常", rowNum, j, e);
                    cell.setBlank();
                }
            }
        }
    }

    /**
     * 高性能字符串替换
     *
     * <p>对于简单的单次替换场景，使用StringBuilder避免String.replace()创建的多个中间对象。
     * String.replace()在内部会创建Pattern和Matcher对象，而此方法更轻量。</p>
     *
     * @param original 原始字符串
     * @param target 目标子串
     * @param replacement 替换子串
     * @return 替换后的字符串
     */
    private static String fastReplace(String original, String target, String replacement) {
        int startPos = original.indexOf(target);
        if (startPos == -1) {
            return original;
        }
        
        StringBuilder sb = new StringBuilder(original.length() + replacement.length());
        int lastEnd = 0;
        while (startPos != -1) {
            sb.append(original, lastEnd, startPos);
            sb.append(replacement);
            lastEnd = startPos + target.length();
            startPos = original.indexOf(target, lastEnd);
        }
        sb.append(original, lastEnd, original.length());
        return sb.toString();
    }

    /**
     * 写入Map类型行数据
     *
     * <p>当未指定映射Class时使用</p>
     *
     * @param row Excel行对象
     * @param data Map类型数据
     */
    private void writeMapRow(Row row, Map<?, ?> data) {
        if (data == null) {
            return;
        }

        int colIndex = 0;
        for (Map.Entry<?, ?> entry : data.entrySet()) {
            Cell cell = row.createCell(colIndex++);
            valueFormatter.setCellValueFast(cell, entry.getValue(), null);
        }
    }

    /**
     * 清空日期格式化缓存
     */
    public static void clearDateFormatCache() {
        ValueFormatter.clearDateFormatCache();
    }

    /**
     * 完成写入并释放资源
     *
     * <p>执行顺序:
     * <ol>
     *   <li>将工作簿内容写入目标输出</li>
     *   <li>刷写输出缓冲</li>
     *   <li>清理SXSSF临时文件</li>
     *   <li>关闭工作簿</li>
     * </ol>
     *
     * <p>使用 try-finally 确保资源正确释放</p>
     *
     * @throws IOException 写入异常
     */
    @SuppressWarnings("deprecation")
    public void finish() throws IOException {
        if (workbook == null) {
            return;
        }

        String filePath = metadata.getFilePath();
        File file = metadata.getFile();
        OutputStream outputStream = metadata.getOutputStream();

        IOException firstException = null;

        try {
            if (outputStream != null) {
                workbook.write(outputStream);
                outputStream.flush();
            } else if (filePath != null) {
                try (FileOutputStream fos = new FileOutputStream(filePath)) {
                    workbook.write(fos);
                    fos.flush();
                }
            } else if (file != null) {
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    workbook.write(fos);
                    fos.flush();
                }
            }
        } catch (IOException e) {
            firstException = e;
        } finally {
            if (!append && !isMultiSheetWriting) {
                try {
                    workbook.close();
                } catch (IOException e) {
                    if (firstException == null) {
                        firstException = e;
                    }
                }
                if (workbook instanceof SXSSFWorkbook) {
                    SXSSFWorkbook sxssf = (SXSSFWorkbook) workbook;
                    sxssf.dispose();
                }
            }
        }

        if (firstException != null) {
            throw firstException;
        }
    }
}