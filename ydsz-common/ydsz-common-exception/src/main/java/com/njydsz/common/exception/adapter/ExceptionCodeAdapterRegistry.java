package com.njydsz.common.exception.adapter;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.lang.Nullable;

import com.njydsz.common.exception.custom.AbstractYdszException;

import lombok.extern.slf4j.Slf4j;

/**
 * 异常码适配器注册中心。
 *
 * <p>收集 Spring 容器中所有 {@link ExceptionCodeAdapter} 实现，
 * 并根据协议类型匹配最佳适配器。
 *
 * <p><b>扩展方式：</b>业务方只需实现 {@link ExceptionCodeAdapter} 并声明为 Spring Bean，
 * 本注册中心会自动发现并按优先级匹配。
 *
 * @author ydsz-team
 * @since 2.4.0
 */
@Slf4j
public class ExceptionCodeAdapterRegistry {

    private final List<ExceptionCodeAdapter> adapters;

    public ExceptionCodeAdapterRegistry(ObjectProvider<ExceptionCodeAdapter> adapterProvider) {
        List<ExceptionCodeAdapter> loaded = adapterProvider.orderedStream().toList();
        this.adapters = Collections.unmodifiableList(loaded);
        if (!this.adapters.isEmpty()) {
            log.info("[ExceptionCodeAdapterRegistry] 已加载 {} 个异常码适配器: {}", this.adapters.size(),
                    this.adapters.stream().map(a -> a.getClass().getSimpleName()).toList());
        }
    }

    /**
     * 查找支持指定协议类型的适配器。
     *
     * @param protocolType 目标协议的错误类型（如 io.grpc.Status.class）
     * @return 适配器Optional（可能为空）
     */
    public Optional<ExceptionCodeAdapter> findAdapter(Class<?> protocolType) {
        return adapters.stream()
                .filter(a -> a.supports(protocolType))
                .min(Comparator.comparingInt(ExceptionCodeAdapter::priority));
    }

    /**
     * 将 ydsz 异常转换为协议错误（带自动匹配）。
     *
     * <p>当无匹配适配器时返回 null，调用方需回退到默认处理。
     *
     * @param ex           ydsz 异常
     * @param protocolType 目标协议类型
     * @return 协议特定的错误表示，或 null（无匹配适配器）
     */
    @Nullable
    public Object adapt(AbstractYdszException ex, Class<?> protocolType) {
        return findAdapter(protocolType)
                .map(adapter -> safeAdapt(adapter, ex))
                .orElse(null);
    }

    /**
     * 安全适配器调用（捕获异常，避免适配器内部错误影响主流程）。
     */
    @Nullable
    private Object safeAdapt(ExceptionCodeAdapter adapter, AbstractYdszException ex) {
        try {
            return adapter.toProtocolError(ex);
        } catch (Exception e) {
            log.warn("[ExceptionCodeAdapterRegistry] 适配器 {} 转换失败: {} | 错误: {}",
                    adapter.getClass().getSimpleName(), ex.getCode(), e.getMessage());
            return null;
        }
    }

    /**
     * 检查是否有适配器支持指定协议。
     *
     * @param protocolType 协议类型
     * @return true-有可用适配器
     */
    public boolean hasAdapterFor(Class<?> protocolType) {
        return findAdapter(protocolType).isPresent();
    }

    /**
     * 当前已注册的适配器列表（只读）。
     *
     * @return 适配器列表
     */
    public List<ExceptionCodeAdapter> getAdapters() {
        return adapters;
    }
}
