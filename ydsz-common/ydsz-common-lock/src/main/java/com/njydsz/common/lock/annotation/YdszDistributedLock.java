package com.njydsz.common.lock.annotation;
import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;


/**
 * 分布式锁注解
 *
 * <p>用于在方法级别声明分布式锁，支持多种锁类型：
 * <ul>
 *   <li>可重入锁：同一线程可多次获取（默认）</li>
 *   <li>公平锁：按请求顺序获取</li>
 * </ul>
 *
 * <p><b>注意：</b>{@link LockType#READ_WRITE} 与 {@link LockType#SEMAPHORE}
 * 为键维度实例，不支持注解方式使用（将抛出 {@code IllegalArgumentException}），
 * 请通过 {@code LockStrategy.getReadWriteLock / getSemaphore} 编程式获取。</p>
 *
 * <p>与 {@link Idempotent} 的区别：本注解用于对同一资源的并发互斥（key 通常含 SpEL 表达式精确到资源 ID，
 * 并可阻塞等待），而 {@link Idempotent} 用于接口防重复提交（key 通常为静态串，不阻塞）。
 * 典型场景下两者会配合使用形成分层防御。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * @YdszDistributedLock(key = "order:#{#orderId}", waitTime = 3, leaseTime = 30)
 * public void processOrder(String orderId) {
 *     // 业务逻辑
 * }
 * }</pre>
 *
 * <p><b>参数说明：</b>
 * <ul>
 *   <li>key：锁的键，支持 SpEL 表达式（模板 {@code "order:#{#orderId}"} 或整串 {@code "'order:' + #orderId"}）</li>
 *   <li>lockType：锁类型，仅支持 REENTRANT / FAIR，默认 REENTRANT</li>
 *   <li>waitTime：最大等待时间，默认 0（不等待）</li>
 *   <li>leaseTime：锁的自动释放时间，默认 30 秒</li>
 *   <li>timeUnit：时间单位，默认秒</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface YdszDistributedLock {

    /**
     * 锁的键
     * <p>支持 SpEL 表达式，如："order:#{#orderId}"
     *
     * @return 锁的键
     */
    String key();

    /**
     * 锁类型
     *
     * @return 锁类型枚举
     */
    LockType lockType() default LockType.REENTRANT;

    /**
     * 最大等待时间
     * <p>为 0 时表示非阻塞，获取不到锁直接返回
     *
     * @return 等待时间
     */
    long waitTime() default 0;

    /**
     * 锁的自动释放时间
     * <p>超过此时间锁自动释放，防止死锁
     *
     * @return 释放时间
     */
    long leaseTime() default 30;

    /**
     * 时间单位
     *
     * @return 时间单位枚举
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /**
     * 获取锁失败时的提示信息
     *
     * @return 提示信息
     */
    String message() default "系统繁忙，请稍后重试";

    /**
     * 是否抛出异常
     * <p>true：获取锁失败时抛出异常
     * false：获取锁失败时跳过方法执行，返回 null 或默认值
     *
     * @return 是否抛出异常
     */
    boolean throwException() default true;

    /**
     * 获取锁失败时的重试次数
     * <p>为 0 表示不重试，默认值 0
     *
     * @return 重试次数
     */
    int retryCount() default 0;

    /**
     * 重试间隔（毫秒）
     * <p>配合 retryCount 使用，采用指数退避策略，默认值 100ms
     *
     * @return 重试间隔（毫秒）
     */
    long retryInterval() default 100;

    /**
     * 是否启用看门狗自动续期
     * <p>true（默认）：启用 WatchDog 自动续期，防止业务执行时间超过锁过期时间
     * <p>false：不启动续期，锁在 leaseTime 到期后自动释放
     * <p>使用场景：快速完成的操作（如简单 CRUD）可设为 false，避免续期开销
     *
     * @return 是否启用看门狗自动续期
     */
    boolean autoRenew() default true;
}
