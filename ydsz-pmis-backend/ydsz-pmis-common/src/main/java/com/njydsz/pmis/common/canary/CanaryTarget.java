package com.njydsz.pmis.common.canary;

/**
 * 灰度目标 SPI（P2-1 架构优化）。
 *
 * <p>各模块实现此接口，注册为 Spring Bean，
 * 由 {@link CanaryRouter} 统一发现并调用。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
public interface CanaryTarget {

    /**
     * 获取模块名。
     *
     * @return 模块标识（message / literule / workflow / project）
     */
    String getModule();

    /**
     * 根据灰度上下文选择版本。
     *
     * @param context 灰度上下文
     * @return 选中的版本标识
     */
    String selectVersion(CanaryContext context);
}
