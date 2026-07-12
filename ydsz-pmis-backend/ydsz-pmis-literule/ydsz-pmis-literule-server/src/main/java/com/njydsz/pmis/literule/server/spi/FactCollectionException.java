package com.njydsz.pmis.literule.server.spi;

import com.njydsz.pmis.common.exception.custom.InfrastructureException;

/**
 * 事实采集异常（P0-2 动态事实采集管道）
 *
 * <p>当 {@link FactProviderRegistry#isFallbackOnError()} 为 false 时，
 * 任一 {@link FactProvider} 调用失败/超时将抛出此异常，中断规则评估流程。
 *
 * <p>继承 {@link InfrastructureException}，纳入 common-exception 统一异常体系。
 *
 * @author ydsz-pmis-team
 * @since 2.1.0
 */
public class FactCollectionException extends InfrastructureException {

    private static final long serialVersionUID = 1L;

    public FactCollectionException(String message) {
        super(message);
    }

    public FactCollectionException(String message, Throwable cause) {
        super(message);
        this.initCause(cause);
    }
}
