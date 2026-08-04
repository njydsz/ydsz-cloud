package com.remisoft.common.excel.core.listener;

/**
 * ReadHandler 类
 *
 * @author remi-team
 * @email remi-dev@remisoft.com
 * @version 1.0.0
 */
import com.remisoft.common.excel.core.context.AnalysisContext;
import com.remisoft.common.excel.core.metadata.ReadMetadata;

/**
 * Excel读取处理器 - 高级读取回调接口
 *
 * <p>提供比ReadListener更细粒度的回调能力,包括表头读取、行读取等。
 * 所有方法都提供默认空实现,可按需覆盖。</p>
 *
 * <h3>与ReadListener的区别</h3>
 * <ul>
 *   <li>ReadListener - 面向数据行,简单易用</li>
 *   <li>ReadHandler - 面向过程事件,功能更全面</li>
 * </ul>
 *
 * @see ReadListener
 * @see ExcelReader
 * @author remi-team
 * @since 1.0.0
 */
public interface ReadHandler {

    /**
     * 读取开始前的回调
     *
     * @param metadata 读取元数据
     */
    default void onStart(ReadMetadata metadata) {
    }

    /**
     * 读取表头时的回调
     *
     * @param context 分析上下文
     * @param headers 表头数组
     */
    default void onReadHead(AnalysisContext context, String[] headers) {
    }

    /**
     * 每读取一行数据时的回调
     *
     * @param context 分析上下文
     * @param rowData 行数据
     */
    default void onReadRow(AnalysisContext context, Object rowData) {
    }

    /**
     * 读取结束后的回调
     *
     * @param metadata 读取元数据
     */
    default void onEnd(ReadMetadata metadata) {
    }

    /**
     * 发生错误时的回调
     *
     * @param metadata 读取元数据
     * @param e 异常信息
     */
    default void onError(ReadMetadata metadata, Exception e) {
    }
}