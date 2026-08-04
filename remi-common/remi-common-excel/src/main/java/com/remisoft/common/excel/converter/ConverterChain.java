package com.remisoft.common.excel.converter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 转换器链 - 责任链模式
 *
 * <p>维护一组按优先级排序的{@link CellValueConverter}，在转换请求时
 * 按优先级顺序查找第一个支持目标类型的转换器并执行转换。</p>
 *
 * <h3>工作流程</h3>
 * <ol>
 *   <li>接收转换请求（rawValue, targetType, context）</li>
 *   <li>按优先级遍历已注册的转换器</li>
 *   <li>找到第一个{@link CellValueConverter#supports(Class)}返回true的转换器</li>
 *   <li>调用该转换器的{@link CellValueConverter#convert}方法</li>
 *   <li>若无匹配转换器，返回null</li>
 * </ol>
 *
 * @author remi-team
 * @email remi-dev@remisoft.com
 * @version 1.0.0
 * @since 1.0.0
 */
public class ConverterChain {

    private final CopyOnWriteArrayList<CellValueConverter> converters = new CopyOnWriteArrayList<>();

    public ConverterChain() {
    }

    /**
     * 使用初始转换器列表构造
     *
     * @param converters 转换器列表
     */
    public ConverterChain(List<CellValueConverter> converters) {
        this.converters.addAll(converters);
        sortConverters();
    }

    /**
     * 执行类型转换
     *
     * <p>按优先级顺序查找支持目标类型的转换器并执行转换。
     * 若无匹配转换器，返回null。</p>
     *
     * @param rawValue 原始值
     * @param targetType 目标类型
     * @param context 转换上下文
     * @return 转换后的值
     */
    public Object convert(Object rawValue, Class<?> targetType, ConvertContext context) {
        for (CellValueConverter converter : converters) {
            if (converter.supports(targetType)) {
                return converter.convert(rawValue, targetType, context);
            }
        }
        return null;
    }

    /**
     * 注册转换器
     *
     * <p>将转换器添加到链中并按优先级重新排序。</p>
     *
     * @param converter 转换器实例
     */
    public void register(CellValueConverter converter) {
        converters.add(converter);
        sortConverters();
    }

    /**
     * 获取已注册的转换器列表（只读视图）
     *
     * @return 不可修改的转换器列表
     */
    public List<CellValueConverter> getConverters() {
        return Collections.unmodifiableList(new ArrayList<>(converters));
    }

    private void sortConverters() {
        converters.sort((a, b) -> Integer.compare(a.priority(), b.priority()));
    }
}
