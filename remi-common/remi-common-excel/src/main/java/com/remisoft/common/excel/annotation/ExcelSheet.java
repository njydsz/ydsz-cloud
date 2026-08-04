package com.remisoft.common.excel.annotation;

/**
 * ExcelSheet 类
 *
 * @author remi-team
 * @email remi-dev@remisoft.com
 * @version 1.0.0
 */
import java.lang.annotation.*;

/**
 * ExcelSheet注解 - Sheet页配置
 *
 * <p>用于配置Excel Sheet页的相关属性,包括名称、表头行号、日期格式等。
 * 该注解标注在Java类的类型级别。</p>
 *
 * <h3>示例</h3>
 * <pre>{@code
 * @ExcelSheet(
 *     name = "用户信息",
 *     headRowNumber = 1,
 *     dateFormat = "yyyy-MM-dd",
 *     freezePane = @FreezePane(row = 1, col = 0),
 *     mergedRegions = {@MergedRegion(startRow = 0, endRow = 0, startCol = 0, endCol = 2)}
 * )
 * public class User {
 *     @ExcelProperty("姓名")
 *     private String name;
 *
 *     @ExcelProperty("生日")
 *     private Date birthday;
 * }
 * }</pre>
 *
 * @see ExcelProperty
 * @see FreezePane
 * @see MergedRegion
 * @author remi-team
 * @since 1.0.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ExcelSheet {

    /**
     * Sheet名称
     *
     * <p>指定要创建或读取的Sheet页名称。
     * 若未指定,写入时使用默认名称"sheet1"</p>
     *
     * @return Sheet名称
     */
    String name() default "";

    /**
     * Sheet序号
     *
     * <p>指定Sheet的序号(从0开始)。
     * 用于多Sheet场景下的精确指定</p>
     *
     * @return Sheet序号
     */
    int sheetNo() default 0;

    /**
     * 表头行号
     *
     * <p>指定表头所在的行号(从0开始计数)。
     * 默认值为1,即第二行作为表头</p>
     *
     * @return 表头行号
     */
    int headRowNumber() default 1;

    /**
     * 数据起始行号(预留)
     *
     * @return 数据起始行号
     */
    int dataRowNumber() default 0;

    /**
     * 默认日期格式
     *
     * <p>当字段未单独指定dateFormat时,使用此默认值。
     * 支持的格式如:"yyyy-MM-dd"、"yyyy/MM/dd HH:mm:ss"等</p>
     *
     * @return 日期格式字符串
     */
    String dateFormat() default "";

    /**
     * Sheet保护密码
     *
     * <p>设置后Sheet将处于保护状态,需要密码才能编辑。
     * 仅在写入时生效</p>
     *
     * @return 保护密码
     */
    String password() default "";

    /**
     * 是否加密
     *
     * <p>设置为true时,对Excel文件进行加密保护(预留功能)</p>
     *
     * @return true表示加密
     */
    boolean encrypted() default false;

    /**
     * 冻结窗格配置
     *
     * <p>用于固定表头或首列,方便查看大数据量时的滚动浏览。
     * 例如 freezePane = @FreezePane(row = 1) 冻结首行,
     * freezePane = @FreezePane(col = 1) 冻结首列</p>
     *
     * @return 冻结窗格配置
     */
    FreezePane freezePane() default @FreezePane;

    /**
     * 合并单元格配置
     *
     * <p>用于设置需要合并的单元格区域,常用于表头多列合并。
     * 例如合并第一行的0-2列为一个单元格</p>
     *
     * @return 合并单元格配置数组
     */
    MergedRegion[] mergedRegions() default {};

    /**
     * 是否自动设置列宽
     *
     * <p>设置为true时,根据内容自动调整列宽</p>
     *
     * @return true表示自动设置列宽
     */
    boolean autoColumnWidth() default false;

    /**
     * 冻结窗格注解
     *
     * <p>用于配置Excel的冻结窗格功能</p>
     *
     * <h3>使用示例</h3>
     * <pre>{@code
     * // 冻结首行
     * @FreezePane(row = 1)
     *
     * // 冻结首列
     * @FreezePane(col = 1)
     *
     * // 冻结首行首列
     * @FreezePane(row = 1, col = 1)
     * }</pre>
     */
    @interface FreezePane {
        /**
         * 冻结的行数(从首行开始)
         *
         * <p>设置为0表示不冻结任何行</p>
         *
         * @return 冻结的行数
         */
        int row() default 0;

        /**
         * 冻结的列数(从首列开始)
         *
         * <p>设置为0表示不冻结任何列</p>
         *
         * @return 冻结的列数
         */
        int col() default 0;
    }

    /**
     * 合并单元格注解
     *
     * <p>用于配置Excel的单元格合并区域</p>
     *
     * <h3>使用示例</h3>
     * <pre>{@code
     * // 合并第1-3行, 第1-2列的区域
     * @MergedRegion(startRow = 0, endRow = 2, startCol = 0, endCol = 1)
     * }</pre>
     */
    @interface MergedRegion {
        /**
         * 起始行号(从0开始)
         *
         * @return 起始行号
         */
        int startRow();

        /**
         * 结束行号(从0开始)
         *
         * @return 结束行号
         */
        int endRow();

        /**
         * 起始列号(从0开始)
         *
         * @return 起始列号
         */
        int startCol();

        /**
         * 结束列号(从0开始)
         *
         * @return 结束列号
         */
        int endCol();
    }
}