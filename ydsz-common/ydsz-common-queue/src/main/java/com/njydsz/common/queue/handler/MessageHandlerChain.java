package com.njydsz.common.queue.handler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.njydsz.common.queue.domain.QueueMessage;
import com.njydsz.common.queue.service.IMessageHandler;

import lombok.extern.slf4j.Slf4j;

/**
 * 无状态消息处理器链
 *
 * <p>实现职责链模式（Chain of Responsibility），将多个 {@link IMessageHandler} 按顺序链接，
 * 消息依次通过每个处理器。适用于横切关注点（日志、校验、脱敏、审计等）的组合。
 *
 * <p><b>无状态设计：</b>
 * <ul>
 *   <li>处理器链本身不持有消息状态，每次 {@link #onMessage(QueueMessage)} 调用独立执行</li>
 *   <li>线程安全，可被多个消费者共享使用</li>
 * </ul>
 *
 * <p><b>执行语义：</b>
 * <ul>
 *   <li>顺序执行：消息按添加顺序依次通过每个处理器</li>
 *   <li>异常短路：任一处理器抛出异常即中断链，异常向上传播</li>
 *   <li>空消息透传：null 消息不经过处理器，直接返回</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 构建处理器链：校验 -> 脱敏 -> 业务处理
 * MessageHandlerChain chain = MessageHandlerChain.builder()
 *     .add(new ValidationHandler())
 *     .add(new DesensitizationHandler())
 *     .add(new BusinessHandler())
 *     .build();
 *
 * subscriber.subscribeAsync(chain);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class MessageHandlerChain implements IMessageHandler {

    private final List<IMessageHandler> handlers;
    private final String name;

    /**
     * 私有构造函数，通过 {@link Builder} 创建实例
     *
     * @param handlers 处理器列表
     * @param name    链名称（用于日志标识）
     */
    private MessageHandlerChain(List<IMessageHandler> handlers, String name) {
        this.handlers = List.copyOf(handlers);
        this.name = name;
    }

    @Override
    public void onMessage(QueueMessage message) throws Exception {
        if (message == null) {
            return;
        }
        if (handlers.isEmpty()) {
            log.debug("[HandlerChain-{}] 处理器链为空，跳过处理", name);
            return;
        }
        for (int i = 0; i < handlers.size(); i++) {
            IMessageHandler handler = handlers.get(i);
            try {
                handler.onMessage(message);
            } catch (Exception e) {
                log.error("[HandlerChain-{}] 第 {}/{} 个处理器执行异常, handler={}, error={}",
                        name, i + 1, handlers.size(), handler.getClass().getSimpleName(), e.getMessage());
                throw e;
            }
        }
    }

    /**
     * 获取链中的处理器数量
     *
     * @return 处理器数量
     */
    public int size() {
        return handlers.size();
    }

    /**
     * 判断链是否为空
     *
     * @return true 如果链中没有处理器
     */
    public boolean isEmpty() {
        return handlers.isEmpty();
    }

    /**
     * 创建 Builder 实例
     *
     * @return 新的 Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 将多个处理器构建为链
     *
     * @param handlers 处理器数组
     * @return 处理器链实例
     */
    public static MessageHandlerChain of(IMessageHandler... handlers) {
        return builder().add(handlers).build();
    }

    /**
     * 处理器链构建器
     */
    public static class Builder {

        private final List<IMessageHandler> handlers = new ArrayList<>();
        private String name = "default";

        /**
         * 添加处理器到链尾
         *
         * @param handler 消息处理器
         * @return 当前 Builder，支持链式调用
         */
        public Builder add(IMessageHandler handler) {
            if (handler != null) {
                handlers.add(handler);
            }
            return this;
        }

        /**
         * 批量添加处理器
         *
         * @param handlers 处理器数组
         * @return 当前 Builder，支持链式调用
         */
        public Builder add(IMessageHandler... handlers) {
            if (handlers != null) {
                Arrays.stream(handlers)
                        .filter(h -> h != null)
                        .forEach(this.handlers::add);
            }
            return this;
        }

        /**
         * 批量添加处理器
         *
         * @param handlers 处理器列表
         * @return 当前 Builder，支持链式调用
         */
        public Builder add(List<IMessageHandler> handlers) {
            if (handlers != null) {
                handlers.stream()
                        .filter(h -> h != null)
                        .forEach(this.handlers::add);
            }
            return this;
        }

        /**
         * 设置链名称
         *
         * @param name 链名称（用于日志标识）
         * @return 当前 Builder，支持链式调用
         */
        public Builder name(String name) {
            if (name != null && !name.trim().isEmpty()) {
                this.name = name;
            }
            return this;
        }

        /**
         * 构建处理器链实例
         *
         * @return 不可变的处理器链
         */
        public MessageHandlerChain build() {
            return new MessageHandlerChain(handlers, name);
        }
    }
}
