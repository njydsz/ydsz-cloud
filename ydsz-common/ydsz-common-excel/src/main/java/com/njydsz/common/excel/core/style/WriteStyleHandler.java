package com.njydsz.common.excel.core.style;

/**
 * WriteStyleHandler 类
 *
 * @author ydsz-team
 * @email ydsz-dev@ydszsoft.com
 * @version 1.0.0
 */
import java.util.HashMap;
import java.util.Map;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import com.njydsz.common.excel.annotation.ExcelStyle;

/**
 * Excel写入样式处理器 - 单元格样式创建与缓存
 *
 * <p>负责创建和管理 Excel 单元格样式，支持表头样式和数据样式的独立配置。
 * 采用样式缓存机制，避免相同配置的样式重复创建，提升性能。</p>
 *
 * <h3>核心功能</h3>
 * <ul>
 *   <li>表头样式创建 - 支持加粗、字体颜色、背景色、对齐方式等</li>
 *   <li>数据样式创建 - 支持数据行的各种样式配置</li>
 *   <li>样式缓存 - 相同配置的样式不会重复创建</li>
 *   <li>颜色解析 - 支持预定义颜色名称到POI颜色索引的转换</li>
 * </ul>
 *
 * <h3>设计模式</h3>
 * <ul>
 *   <li>享元模式 - 通过缓存复用相同样式</li>
 *   <li>构建器模式 - StyleKey 使用 Builder 模式构建缓存键</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * WriteStyleHandler handler = new WriteStyleHandler(workbook);
 *
 * // 获取表头样式
 * CellStyle headStyle = handler.getHeadStyle(annotation);
 *
 * // 获取数据样式
 * CellStyle dataStyle = handler.getDataStyle(annotation);
 *
 * // 应用样式
 * cell.setCellStyle(headStyle);
 * }</pre>
 *
 * @see CellStyle
 * @see Font
 * @see ExcelStyle
 * @author ydsz-team
 * @since 1.0.0
 */
public class WriteStyleHandler {

    /** 工作簿引用，用于创建样式和字体 */
    private final Workbook workbook;

    /** 表头样式缓存，避免重复创建相同样式 */
    private final Map<String, CellStyle> headStyleCache;

    /** 数据样式缓存，避免重复创建相同样式 */
    private final Map<String, CellStyle> dataStyleCache;

    /** 颜色名称到索引的缓存 */
    private final Map<String, Short> colorIndexCache;

    /**
     * 构造函数
     *
     * @param workbook Apache POI 工作簿对象
     */
    public WriteStyleHandler(Workbook workbook) {
        this.workbook = workbook;
        this.headStyleCache = new HashMap<>();
        this.dataStyleCache = new HashMap<>();
        this.colorIndexCache = new HashMap<>();
        initColorCache();
    }

    /**
     * 初始化预定义颜色缓存
     *
     * <p>支持的颜色名称: RED, BLUE, GREEN, YELLOW, WHITE, BLACK, GRAY_25_PERCENT, GRAY_50_PERCENT, AUTO</p>
     */
    private void initColorCache() {
        colorIndexCache.put("RED", IndexedColors.RED.getIndex());
        colorIndexCache.put("BLUE", IndexedColors.BLUE.getIndex());
        colorIndexCache.put("GREEN", IndexedColors.GREEN.getIndex());
        colorIndexCache.put("YELLOW", IndexedColors.YELLOW.getIndex());
        colorIndexCache.put("WHITE", IndexedColors.WHITE.getIndex());
        colorIndexCache.put("BLACK", IndexedColors.BLACK.getIndex());
        colorIndexCache.put("GRAY_25_PERCENT", IndexedColors.GREY_25_PERCENT.getIndex());
        colorIndexCache.put("GRAY_50_PERCENT", IndexedColors.GREY_50_PERCENT.getIndex());
        colorIndexCache.put("AUTO", IndexedColors.AUTOMATIC.getIndex());
    }

    /**
     * 获取表头样式
     *
     * <p>如果样式已缓存则直接返回，否则创建新样式并缓存</p>
     *
     * @param annotation 样式注解，如果为 null 则返回默认表头样式
     * @return 表头单元格样式
     */
    public CellStyle getHeadStyle(ExcelStyle annotation) {
        if (annotation == null) {
            return createDefaultHeadStyle();
        }

        String key = buildStyleKey(annotation, true);
        if (headStyleCache.containsKey(key)) {
            return headStyleCache.get(key);
        }

        CellStyle style = createHeadStyle(annotation);
        headStyleCache.put(key, style);
        return style;
    }

    /**
     * 获取数据样式
     *
     * <p>如果样式已缓存则直接返回，否则创建新样式并缓存</p>
     *
     * @param annotation 样式注解，如果为 null 则返回默认数据样式
     * @return 数据单元格样式
     */
    public CellStyle getDataStyle(ExcelStyle annotation) {
        if (annotation == null) {
            return createDefaultDataStyle();
        }

        String key = buildStyleKey(annotation, false);
        if (dataStyleCache.containsKey(key)) {
            return dataStyleCache.get(key);
        }

        CellStyle style = createDataStyle(annotation);
        dataStyleCache.put(key, style);
        return style;
    }

    /**
     * 构建样式缓存键
     *
     * <p>根据注解属性构建唯一的缓存键</p>
     *
     * @param annotation 样式注解
     * @param isHead 是否为表头样式
     * @return 缓存键字符串
     */
    private String buildStyleKey(ExcelStyle annotation, boolean isHead) {
        if (isHead) {
            return String.format("%s_%s_%d_%s",
                annotation.headFontColor(),
                annotation.headBackgroundColor(),
                annotation.headFontSize(),
                annotation.headHorizontalAlignment());
        } else {
            return String.format("%s_%s_%d_%s_%s_%s",
                annotation.dataFontColor(),
                annotation.dataBackgroundColor(),
                annotation.dataFontSize(),
                annotation.dataHorizontalAlignment(),
                annotation.dataVerticalAlignment(),
                annotation.wrapText());
        }
    }

    /**
     * 创建默认表头样式
     *
     * <p>默认样式特点:
     * <ul>
     *   <li>加粗字体 (Calibri, 11pt)</li>
     *   <li>25%灰色背景</li>
     *   <li>水平和垂直居中</li>
     *   <li>双线边框</li>
     *   <li>自动换行</li>
     * </ul>
     *
     * @return 默认表头样式
     */
    private CellStyle createDefaultHeadStyle() {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontName("Calibri");
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setWrapText(true);
        return style;
    }

    /**
     * 创建默认数据样式
     *
     * <p>默认样式特点:
     * <ul>
     *   <li>非加粗字体 (Calibri, 10pt)</li>
     *   <li>左对齐</li>
     *   <li>垂直居中</li>
     * </ul>
     *
     * @return 默认数据样式
     */
    private CellStyle createDefaultDataStyle() {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(false);
        font.setFontName("Calibri");
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    /**
     * 根据注解创建表头样式
     *
     * @param annotation 样式注解
     * @return 表头单元格样式
     */
    private CellStyle createHeadStyle(ExcelStyle annotation) {
        CellStyle style = workbook.createCellStyle();

        Font font = workbook.createFont();
        font.setBold(annotation.headBold());
        font.setFontName("Calibri");
        font.setFontHeightInPoints(annotation.headFontSize());
        font.setColor(getColorIndex(annotation.headFontColor()));
        style.setFont(font);

        short bgColorIndex = getColorIndex(annotation.headBackgroundColor());
        style.setFillForegroundColor(bgColorIndex);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        style.setAlignment(parseHorizontalAlignment(annotation.headHorizontalAlignment()));
        style.setVerticalAlignment(VerticalAlignment.CENTER);

        if (annotation.border()) {
            style.setBorderBottom(parseBorderStyle(annotation.borderStyle()));
            style.setBorderTop(parseBorderStyle(annotation.borderStyle()));
            style.setBorderLeft(parseBorderStyle(annotation.borderStyle()));
            style.setBorderRight(parseBorderStyle(annotation.borderStyle()));
        }

        if (annotation.wrapText()) {
            style.setWrapText(true);
        }

        return style;
    }

    /**
     * 根据注解创建数据样式
     *
     * @param annotation 样式注解
     * @return 数据单元格样式
     */
    private CellStyle createDataStyle(ExcelStyle annotation) {
        CellStyle style = workbook.createCellStyle();

        Font font = workbook.createFont();
        font.setBold(annotation.dataBold());
        font.setFontName("Calibri");
        font.setFontHeightInPoints(annotation.dataFontSize());
        font.setColor(getColorIndex(annotation.dataFontColor()));
        style.setFont(font);

        String bgColor = annotation.dataBackgroundColor();
        if (bgColor != null && !bgColor.equalsIgnoreCase("NO_FILL") && !bgColor.equalsIgnoreCase("NONE")) {
            short bgColorIndex = getColorIndex(bgColor);
            style.setFillForegroundColor(bgColorIndex);
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }

        style.setAlignment(parseHorizontalAlignment(annotation.dataHorizontalAlignment()));
        style.setVerticalAlignment(parseVerticalAlignment(annotation.dataVerticalAlignment()));

        if (annotation.border()) {
            style.setBorderBottom(parseBorderStyle(annotation.borderStyle()));
            style.setBorderTop(parseBorderStyle(annotation.borderStyle()));
            style.setBorderLeft(parseBorderStyle(annotation.borderStyle()));
            style.setBorderRight(parseBorderStyle(annotation.borderStyle()));
        }

        if (annotation.wrapText()) {
            style.setWrapText(true);
        }

        return style;
    }

    /**
     * 将颜色名称转换为 POI 颜色索引
     *
     * <p>支持的颜色名称包括: RED, BLUE, GREEN, YELLOW, WHITE, BLACK 等</p>
     *
     * @param colorName 颜色名称
     * @return POI 颜色索引，未知颜色默认返回黑色
     */
    private short getColorIndex(String colorName) {
        if (colorName == null || colorName.isEmpty()) {
            return IndexedColors.BLACK.getIndex();
        }

        if (colorIndexCache.containsKey(colorName)) {
            return colorIndexCache.get(colorName);
        }

        return IndexedColors.BLACK.getIndex();
    }

    /**
     * 解析水平对齐方式
     *
     * @param alignment 对齐方式字符串 (CENTER, LEFT, RIGHT)
     * @return POI HorizontalAlignment
     */
    private HorizontalAlignment parseHorizontalAlignment(String alignment) {
        if (alignment == null) {
            return HorizontalAlignment.LEFT;
        }
        switch (alignment.toUpperCase()) {
            case "CENTER":
                return HorizontalAlignment.CENTER;
            case "RIGHT":
                return HorizontalAlignment.RIGHT;
            case "LEFT":
            default:
                return HorizontalAlignment.LEFT;
        }
    }

    /**
     * 解析垂直对齐方式
     *
     * @param alignment 对齐方式字符串 (CENTER, TOP, BOTTOM)
     * @return POI VerticalAlignment
     */
    private VerticalAlignment parseVerticalAlignment(String alignment) {
        if (alignment == null) {
            return VerticalAlignment.CENTER;
        }
        switch (alignment.toUpperCase()) {
            case "TOP":
                return VerticalAlignment.TOP;
            case "BOTTOM":
                return VerticalAlignment.BOTTOM;
            case "CENTER":
            default:
                return VerticalAlignment.CENTER;
        }
    }

    /**
     * 解析边框样式
     *
     * @param borderStyle 边框样式字符串 (THIN, MEDIUM, THICK 等)
     * @return POI BorderStyle
     */
    private BorderStyle parseBorderStyle(String borderStyle) {
        if (borderStyle == null) {
            return BorderStyle.THIN;
        }
        try {
            return BorderStyle.valueOf(borderStyle.toUpperCase());
        } catch (IllegalArgumentException e) {
            return BorderStyle.THIN;
        }
    }
}
