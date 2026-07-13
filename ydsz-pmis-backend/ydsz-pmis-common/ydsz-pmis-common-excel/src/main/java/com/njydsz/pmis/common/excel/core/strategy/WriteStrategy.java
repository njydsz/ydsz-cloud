package com.njydsz.pmis.common.excel.core.strategy;

import com.njydsz.pmis.common.excel.core.context.WriteContext;
import com.njydsz.pmis.common.excel.core.metadata.WriteMetadata;
import com.njydsz.pmis.common.excel.core.ExcelWriter;

/**
 * Excel写入策略接口 - 策略模式实现
 *
 * <p>定义Excel写入的不同策略实现,支持多种写入模式:
 * <ul>
 *   <li>SXSSF流式写入 - 支持大文件,内存占用低</li>
 *   <li>普通写入 - 适合中小文件</li>
 *   <li>超高速写入 - 直接生成XML,性能最高</li>
 * </ul>
 *
 * <h3>设计模式</h3>
 * <ul>
 *   <li>策略模式 - 不同写入策略可互换</li>
 *   <li>模板方法模式 - doWrite定义算法骨架</li>
 * </ul>
 *
 * @see ExcelWriter
 * @author ydsz-pmis-team
 * @email pmis-dev@njydsz.com
 * @version 1.0.0
 */
public interface WriteStrategy {

    /**
     * 执行Excel写入
     *
     * @param metadata 写入元数据
     * @param data 要写入的数据
     * @param context 写入上下文
     */
    void doWrite(WriteMetadata metadata, Object data, WriteContext context);

    /**
     * 获取策略名称
     *
     * @return 策略名称,用于日志和调试
     */
    String getName();

    /**
     * 判断是否支持当前写入模式
     *
     * <p>默认实现:当数据量大于阈值时自动选择流式策略</p>
     *
     * @param metadata 写入元数据
     * @param data 要写入的数据
     * @return true表示支持该元数据配置
     */
    default boolean supports(WriteMetadata metadata, Object data) {
        return true;
    }

    /**
     * 获取优先级
     *
     * <p>用于自动策略选择时,优先级高的策略会被优先考虑</p>
     *
     * @return 优先级,数值越小优先级越高
     */
    default int getPriority() {
        return 100;
    }

    /**
     * 是否使用此策略进行写入
     *
     * <p>在自动选择策略时,会调用此方法判断是否使用此策略</p>
     *
     * @param metadata 写入元数据
     * @param data 要写入的数据
     * @return true表示使用此策略
     */
    default boolean shouldUse(WriteMetadata metadata, Object data) {
        return supports(metadata, data);
    }
}
