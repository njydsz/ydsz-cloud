package com.remisoft.common.file.storage;

import java.util.function.Supplier;

import com.remisoft.common.exception.custom.BusinessException;
import com.remisoft.common.file.exception.FileExceptionCode;

import lombok.extern.slf4j.Slf4j;

/**
 * 存储操作重试助手（指数退避策略）
 * <p>对存储操作（上传/下载等）提供自动重试能力，采用指数退避算法，
 * 避免在云存储服务短暂不可用时直接失败。
 *
 * <p><b>重试策略：</b></p>
 * <ul>
 *   <li>首次失败后等待 {@code initialBackoffMillis} 毫秒</li>
 *   <li>每次重试等待时间翻倍（指数退避）</li>
 *   <li>达到最大重试次数后抛出 {@link BusinessException}</li>
 * </ul>
 *
 * <p><b>注意事项：</b></p>
 * <ul>
 *   <li>业务异常（{@link BusinessException}）不会重试，直接抛出</li>
 *   <li>仅对系统异常/网络异常进行重试</li>
 *   <li>线程中断时立即退出重试循环</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 */
@Slf4j
public class StorageRetryHelper {

    /** 最大重试次数 */
    private final int maxRetries;

    /** 初始退避时间（毫秒） */
    private final long initialBackoffMillis;

    /**
     * 构造存储重试助手
     *
     * @param maxRetries            最大重试次数（最小为 0，表示不重试）
     * @param initialBackoffMillis  初始退避时间（毫秒，最小为 100）
     */
    public StorageRetryHelper(int maxRetries, long initialBackoffMillis) {
        this.maxRetries = Math.max(0, maxRetries);
        this.initialBackoffMillis = Math.max(100, initialBackoffMillis);
    }

    /**
     * 带重试执行操作（有返回值）
     * <p>若操作成功则直接返回结果；若操作失败且为系统异常则自动重试；
     * 若为业务异常则直接抛出，不重试。
     *
     * @param <T>           返回值类型
     * @param action        待执行的操作
     * @param operationName 操作名称（用于日志记录）
     * @return 操作结果
     * @throws BusinessException 达到最大重试次数或业务异常时抛出
     */
    public <T> T executeWithRetry(Supplier<T> action, String operationName) {
        int attempts = 0;
        while (attempts <= maxRetries) {
            try {
                return action.get();
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                attempts++;
                if (attempts > maxRetries) break;
                long backoff = initialBackoffMillis * (1L << Math.min(attempts - 1, 10));
                log.warn("StorageRetry {} attempt {} failed, retrying in {}ms: {}", operationName, attempts, backoff, e.getMessage());
                try { Thread.sleep(backoff); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }
        }
        log.error("StorageRetry {} failed after {} attempts", operationName, attempts);
        throw new BusinessException(FileExceptionCode.UNKNOWN);
    }

    /**
     * 带重试执行操作（无返回值）
     *
     * @param action        待执行的操作
     * @param operationName 操作名称（用于日志记录）
     */
    public void executeRunnableWithRetry(Runnable action, String operationName) {
        executeWithRetry(() -> { action.run(); return null; }, operationName);
    }

    /**
     * 获取最大重试次数
     *
     * @return 最大重试次数
     */
    public int getMaxRetries() {
        return maxRetries;
    }
}
