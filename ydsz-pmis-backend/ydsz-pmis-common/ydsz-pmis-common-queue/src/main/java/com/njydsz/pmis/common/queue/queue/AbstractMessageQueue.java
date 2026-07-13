package com.njydsz.pmis.common.queue.queue;

import java.util.concurrent.locks.ReentrantLock;

import com.njydsz.pmis.common.queue.service.IMessagePublisher;
import com.njydsz.pmis.common.queue.service.IMessageSubscriber;

/**
 * 消息队列抽象基类
 *
 * <p>提供消息队列的通用实现,包括:
 * <ul>
 *   <li>队列关闭状态管理</li>
 *   <li>关闭状态检查</li>
 *   <li>通用的关闭逻辑模板</li>
 * </ul>
 *
 * <p>子类需要实现:
 * <ul>
 *   <li>{@link #createPublisher(String)} - 创建消息发布者</li>
 *   <li>{@link #createSubscriber(String)} - 创建消息订阅者</li>
 *   <li>{@link #doClose()} - 执行实际的关闭操作</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
public abstract class AbstractMessageQueue implements IMessageQueue {

    /** 队列类型描述 */
    private final String queueType;
    
    /** 队列关闭状态 */
    private volatile boolean closed = false;

    /**
     * 关闭操作互斥锁
     *
     * <p>使用 ReentrantLock 替代 synchronized，避免 JDK 21 虚拟线程被固定（VT pinning）。
     */
    private final ReentrantLock closeLock = new ReentrantLock();

    /**
     * 构造函数
     *
     * @param queueType 队列类型描述
     */
    protected AbstractMessageQueue(String queueType) {
        this.queueType = queueType;
    }

    /**
     * 创建消息发布者
     *
     * @param channel 通道名称
     * @return 消息发布者实例
     */
    @Override
    public abstract IMessagePublisher createPublisher(String channel);

    /**
     * 创建消息订阅者
     *
     * @param channel 通道名称
     * @return 消息订阅者实例
     */
    @Override
    public abstract IMessageSubscriber createSubscriber(String channel);

    /**
     * 获取队列类型描述
     *
     * @return 队列类型字符串
     */
    @Override
    public String getType() {
        return queueType;
    }

    /**
     * 检查队列是否已关闭
     *
     * @return true 如果已关闭,false 如果仍在使用
     */
    @Override
    public boolean isClosed() {
        return closed;
    }

    /**
     * 检查队列是否已关闭,如果已关闭则抛出异常
     *
     * @throws IllegalStateException 如果队列已关闭
     */
    @Override
    public void checkNotClosed() {
        if (closed) {
            throw new IllegalStateException("队列已关闭,无法继续操作");
        }
    }

    /**
     * 关闭队列
     *
     * <p>执行关闭操作:
     * <ol>
     *   <li>检查是否已关闭,避免重复关闭</li>
     *   <li>标记为已关闭状态</li>
     *   <li>调用子类的 doClose() 执行实际关闭逻辑</li>
     * </ol>
     */
    @Override
    public final void close() {
        if (closed) {
            return;
        }
        closeLock.lock();
        try {
            if (closed) {
                return;
            }
            closed = true;
            doClose();
        } finally {
            closeLock.unlock();
        }
    }

    /**
     * 执行实际的关闭操作
     *
     * <p>子类需要实现此方法,执行特定队列类型的关闭逻辑,
     * 如关闭连接、释放资源等。
     */
    protected abstract void doClose();
}
