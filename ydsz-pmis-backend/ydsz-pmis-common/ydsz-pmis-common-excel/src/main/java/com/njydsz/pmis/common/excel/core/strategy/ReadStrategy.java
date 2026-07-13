package com.njydsz.pmis.common.excel.core.strategy;

import com.njydsz.pmis.common.excel.core.context.AnalysisContext;
import com.njydsz.pmis.common.excel.core.listener.ReadListener;
import com.njydsz.pmis.common.excel.core.metadata.ReadMetadata;

/**
 * Excel读取策略接口 - 策略模式实现
 *
 * <p>定义Excel读取的不同策略实现,支持多种读取模式:
 * <ul>
 *   <li>用户模式(UserModel) - 使用POI的对象模型,适合小文件</li>
 *   <li>SAX模式 - 基于事件驱动解析,适合大文件,内存占用低</li>
 *   <li>流式模式(Streaming) - 边读边处理,适合超大数据量</li>
 * </ul>
 *
 * <h3>设计模式</h3>
 * <ul>
 *   <li>策略模式 - 不同读取策略可互换</li>
 *   <li>模板方法模式 -doRead定义算法骨架</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 默认使用用户模式
 * ExcelReader reader = ExcelFacade.read("small.xlsx", User.class);
 * reader.doRead(listener);
 *
 * // 使用SAX模式读取大文件
 * ExcelReader reader = ExcelFacade.read("large.xlsx", User.class);
 * reader.setReadStrategy(new SaxReadStrategy());
 * reader.doRead(listener);
 *
 * // 使用流式模式读取超大数据
 * ExcelReader reader = ExcelFacade.read("huge.xlsx", User.class);
 * reader.setReadStrategy(new StreamingReadStrategy(1000)); // 每1000行处理一次
 * reader.doRead(listener);
 * }</pre>
 *
 * @see UserModelReadStrategy
 * @see SaxReadStrategy
 * @see StreamingReadStrategy
 * @author ydsz-pmis-team
 * @email pmis-dev@njydsz.com
 * @version 1.0.0
 */
public interface ReadStrategy {

    /**
     * 执行Excel读取
     *
     * <p>模板方法,定义读取算法骨架:
     * <ol>
     *   <li>初始化读取环境</li>
     *   <li>解析表头建立映射</li>
     *   <li>逐行读取并通知监听器</li>
     *   <li>清理资源</li>
     * </ol>
     *
     * @param metadata 读取元数据
     * @param listener 数据监听器
     * @param context 分析上下文
     */
    void doRead(ReadMetadata metadata, ReadListener<?> listener, AnalysisContext context);

    /**
     * 获取策略名称
     *
     * @return 策略名称,用于日志和调试
     */
    String getName();

    /**
     * 判断是否支持当前读取模式
     *
     * @param metadata 读取元数据
     * @return true表示支持该元数据配置
     */
    default boolean supports(ReadMetadata metadata) {
        return true;
    }
}