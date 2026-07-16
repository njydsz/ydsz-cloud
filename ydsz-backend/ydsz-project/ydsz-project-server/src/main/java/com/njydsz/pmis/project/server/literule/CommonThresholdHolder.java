package com.njydsz.project.server.literule;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.config.ThresholdProvider;

/**
 * common-core 模块 {@link ThresholdProvider} 的持有器。
 *
 * <p>用于解决 literule SPI 的 {@code ThresholdProvider} 与 common-core 的
 * {@code ThresholdProvider} 同名冲突，避免在 {@link ThresholdProviderBridge} 中使用行内 FQN。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Component
public class CommonThresholdHolder {

    /** common 模块阈值提供器（委托目标） */
    private final ThresholdProvider delegate;

    public CommonThresholdHolder(@Qualifier("thresholdProvider") ThresholdProvider delegate) {
        this.delegate = delegate;
    }

    /**
     * 获取 common-core 模块的阈值提供器实例。
     *
     * @return 阈值提供器
     */
    public ThresholdProvider getDelegate() {
        return delegate;
    }
}
