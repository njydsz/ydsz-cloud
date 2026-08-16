package com.njydsz.common.sentry;

import com.njydsz.common.sentry.SentryObservation.SpiBundle;

/**
 * SPI 加载器（包可见）。
 *
 * <p>为 {@link SentryObservation} 提供 SPI 聚合对象的创建工厂；
 * 初始返回空Bundle，由 {@link SentryObservation#register} 后续填充。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
final class ServiceLoaderFacade {

    private ServiceLoaderFacade() {
        // 工具类，禁止实例化
    }

    /**
     * 创建空的 SPI 聚合对象。
     *
     * <p>实际填充由 {@link SentryAutoConfiguration} 通过 {@link SentryObservation#register} 完成。
     *
     * @return 空的 SPI Bundle
     */
    static SpiBundle load() {
        return new SpiBundle();
    }
}
