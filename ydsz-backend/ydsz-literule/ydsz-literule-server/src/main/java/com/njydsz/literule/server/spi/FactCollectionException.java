package com.njydsz.literule.server.spi;

import com.njydsz.common.exception.custom.SysException;

/**
 * 事实采集异常（P0-2 动态事实采集管道）
 *
 * <p>当 {@link FactProviderRegistry#isFallbackOnError()} 为 false 时，
 * 任一 {@link FactProvider} 调用失败/超时将抛出此异常，中断规则评估流程。
 *
 * <p>继承 {@link SysException}，纳入 common-exception 统一异常体系。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
public class FactCollectionException extends SysException {

    private static final long serialVersionUID = 1L;

    public FactCollectionException(String message) {
        super(message);
    }

    public FactCollectionException(String message, Throwable cause) {
        super(message);
        initCause(cause);
    }
}
