package com.njydsz.common.batch.retry;

import lombok.Data;

/**
 * 重试上下文
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class RetryContext {

    /** 已重试次数 */
    private int retryCount;

    /** 触发的异常 */
    private Throwable throwable;

    /** 上次重试时间戳 */
    private long lastRetryTime;

    /** 资源名 */
    private String resource;
}
