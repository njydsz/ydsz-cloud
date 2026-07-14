package com.njydsz.pmis.common.util.bean;

import java.util.function.BiConsumer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Bean 拷贝选项配置类
 * <p>
 * 提供灵活的拷贝配置选项，支持：
 * 1. 忽略字段配置
 * 2. 是否忽略 null 值
 * 3. 拷贝后处理器
 * </p>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BeanCopyOptions {

    /**
     * 是否忽略 null 值（默认 false）
     * 如果为 true，则源对象中 null 值的属性不会拷贝到目标对象
     */
    @Builder.Default
    private boolean ignoreNull = false;

    /**
     * 要忽略的字段名数组
     * 这些字段在拷贝时会被跳过
     */
    private String[] ignoreProperties;

    /**
     * 拷贝完成后的处理器
     * 可用于在拷贝完成后执行额外的处理逻辑
     */
    private BiConsumer<Object, Object> afterCopyHandler;

    /**
     * 创建默认的 BeanCopyOptions
     *
     * @return BeanCopyOptions 实例
     */
    public static BeanCopyOptions defaults() {
        return new BeanCopyOptions();
    }

    /**
     * 创建忽略 null 值的选项
     *
     * @return BeanCopyOptions 实例
     */
    public static BeanCopyOptions ignoreNullValues() {
        return BeanCopyOptions.builder()
                .ignoreNull(true)
                .build();
    }

    /**
     * 创建忽略指定字段的选项
     *
     * @param properties 要忽略的字段名
     * @return BeanCopyOptions 实例
     */
    public static BeanCopyOptions ignoreProperties(String... properties) {
        return BeanCopyOptions.builder()
                .ignoreProperties(properties)
                .build();
    }

    /**
     * 添加拷贝后处理器
     *
     * @param handler 处理器
     * @return BeanCopyOptions 实例（支持链式调用）
     */
    public BeanCopyOptions withAfterCopyHandler(BiConsumer<Object, Object> handler) {
        this.afterCopyHandler = handler;
        return this;
    }

    /**
     * 设置忽略 null 值
     *
     * @param ignoreNull 是否忽略 null 值
     * @return BeanCopyOptions 实例（支持链式调用）
     */
    public BeanCopyOptions withIgnoreNull(boolean ignoreNull) {
        this.ignoreNull = ignoreNull;
        return this;
    }

    /**
     * 设置忽略字段
     *
     * @param properties 要忽略的字段名
     * @return BeanCopyOptions 实例（支持链式调用）
     */
    public BeanCopyOptions withIgnoreProperties(String... properties) {
        this.ignoreProperties = properties;
        return this;
    }
}
