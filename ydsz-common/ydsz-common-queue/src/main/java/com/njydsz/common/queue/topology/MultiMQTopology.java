package com.njydsz.common.queue.topology;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.njydsz.common.queue.domain.QueueMessage;
import com.njydsz.common.queue.queue.IMessageQueue;
import com.njydsz.common.queue.service.IMessageHandler;
import com.njydsz.common.queue.service.IMessagePublisher;
import com.njydsz.common.queue.service.IMessageSubscriber;

import lombok.extern.slf4j.Slf4j;

/**
 * 多 MQ 组合拓扑实现
 *
 * <p>支持主备切换、扇出和多源聚合三种拓扑模式，通过组合多个 {@link IMessageQueue} 实例
 * 实现高可用、广播和汇聚等高级消息路由能力。
 *
 * <p><b>拓扑模式说明：</b>
 * <ul>
 *   <li>主备模式：写入时优先使用主 MQ，主故障时自动降级到备 MQ；消费时主 MQ 优先，主不可用时切备 MQ</li>
 *   <li>扇出模式：写入时消息同时发往所有 MQ；消费时为每个 MQ 注册独立订阅</li>
 *   <li>聚合模式：写入时使用第一个 MQ；消费时从所有 MQ 汇聚消息到同一 handler</li>
 * </ul>
 *
 * <p><b>线程安全性：</b>拓扑实例创建后参与者列表不可变，线程安全。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class MultiMQTopology {

    private final String name;
    private final TopologyType topologyType;
    private final List<IMessageQueue> participants;
    private final List<IMessageSubscriber> activeSubscribers = new CopyOnWriteArrayList<>();

    /**
     * 创建多 MQ 拓扑实例
     *
     * @param name         拓扑名称（用于日志和监控）
     * @param topologyType 拓扑类型
     * @param participants 参与拓扑的 MQ 实例列表（至少 2 个）
     */
    public MultiMQTopology(String name, TopologyType topologyType, List<IMessageQueue> participants) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("拓扑名称不能为空");
        }
        if (topologyType == null) {
            throw new IllegalArgumentException("拓扑类型不能为空");
        }
        if (participants == null || participants.size() < 2) {
            throw new IllegalArgumentException("拓扑至少需要 2 个 MQ 参与者");
        }
        this.name = name;
        this.topologyType = topologyType;
        this.participants = new ArrayList<>(participants);
        log.info("[MultiMQTopology] 创建 {} 拓扑 '{}', 参与者: {}", topologyType, name, participants.size());
    }

    /**
     * 获取拓扑名称
     *
     * @return 拓扑名称
     */
    public String getName() {
        return name;
    }

    /**
     * 获取拓扑类型
     *
     * @return 拓扑类型
     */
    public TopologyType getTopologyType() {
        return topologyType;
    }

    /**
     * 获取参与拓扑的 MQ 实例列表
     *
     * @return 参与者列表（不可变视图）
     */
    public List<IMessageQueue> getParticipants() {
        return List.copyOf(participants);
    }

    /**
     * 根据拓扑类型创建发布者
     *
     * @param channel 通道/主题名称
     * @return 拓扑感知的发布者
     */
    public IMessagePublisher createPublisher(String channel) {
        List<IMessagePublisher> publishers = new ArrayList<>();
        for (IMessageQueue mq : participants) {
            try {
                publishers.add(mq.createPublisher(channel));
            } catch (Exception e) {
                log.warn("[MultiMQTopology] 创建发布者异常, mq={}, channel={}, error={}",
                        mq.getType(), channel, e.getMessage());
            }
        }
        if (publishers.isEmpty()) {
            throw new IllegalStateException("拓扑中没有任何可用的发布者: " + name);
        }
        return new TopologyPublisher(name, topologyType, publishers);
    }

    /**
     * 根据拓扑类型创建订阅者
     *
     * @param channel 通道/主题名称
     * @return 拓扑感知的订阅者
     */
    public IMessageSubscriber createSubscriber(String channel) {
        if (topologyType == TopologyType.AGGREGATION) {
            // 聚合模式：从所有 MQ 汇聚消费
            return createAggregationSubscriber(channel);
        }
        // 主备/扇出模式：使用主 MQ（第一个）的订阅者
        return createPrimarySubscriber(channel);
    }

    /**
     * 创建主 MQ 订阅者（主备模式下优先使用主 MQ）
     */
    private IMessageSubscriber createPrimarySubscriber(String channel) {
        for (IMessageQueue mq : participants) {
            try {
                return mq.createSubscriber(channel);
            } catch (Exception e) {
                log.warn("[MultiMQTopology] 主 MQ 订阅失败，尝试下一个, mq={}, error={}",
                        mq.getType(), e.getMessage());
            }
        }
        throw new IllegalStateException("拓扑中没有任何可用的订阅者: " + name);
    }

    /**
     * 创建聚合订阅者（从所有 MQ 消费）
     */
    private IMessageSubscriber createAggregationSubscriber(String channel) {
        List<IMessageSubscriber> subscribers = new ArrayList<>();
        for (IMessageQueue mq : participants) {
            try {
                IMessageSubscriber subscriber = mq.createSubscriber(channel);
                subscribers.add(subscriber);
            } catch (Exception e) {
                log.warn("[MultiMQTopology] 创建聚合订阅者异常, mq={}, error={}",
                        mq.getType(), e.getMessage());
            }
        }
        if (subscribers.isEmpty()) {
            throw new IllegalStateException("拓扑中没有任何可用的订阅者: " + name);
        }
        return new AggregationSubscriber(name, subscribers);
    }

    /**
     * 关闭拓扑，释放所有活跃订阅者资源
     */
    public void close() {
        log.info("[MultiMQTopology] 关闭拓扑 '{}', 活跃订阅者: {}", name, activeSubscribers.size());
        for (IMessageSubscriber subscriber : activeSubscribers) {
            try {
                subscriber.stop();
            } catch (Exception e) {
                log.warn("[MultiMQTopology] 关闭订阅者异常: {}", e.getMessage());
            }
        }
        activeSubscribers.clear();
        for (IMessageQueue mq : participants) {
            try {
                mq.close();
            } catch (Exception e) {
                log.warn("[MultiMQTopology] 关闭 MQ 异常, type={}: {}", mq.getType(), e.getMessage());
            }
        }
    }

    /**
     * 拓扑感知的发布者内部类
     */
    private static class TopologyPublisher implements IMessagePublisher {

        private final String topologyName;
        private final TopologyType type;
        private final List<IMessagePublisher> publishers;

        TopologyPublisher(String topologyName, TopologyType type, List<IMessagePublisher> publishers) {
            this.topologyName = topologyName;
            this.type = type;
            this.publishers = publishers;
        }

        @Override
        public void publish(String message) {
            if (type == TopologyType.FAN_OUT) {
                // 扇出模式：发送到所有 MQ
                publishFanOut(message);
            } else {
                // 主备模式：优先主 MQ，失败时降级
                publishPrimaryBackup(message);
            }
        }

        @Override
        public void publish(QueueMessage message) {
            publish(QueueMessage.toPayload(message));
        }

        /**
         * 扇出模式发布：发送到所有 MQ，任一失败记录 WARN 但不阻断
         */
        private void publishFanOut(String message) {
            int successCount = 0;
            for (IMessagePublisher publisher : publishers) {
                try {
                    publisher.publish(message);
                    successCount++;
                } catch (Exception e) {
                    log.warn("[MultiMQTopology] 扇出发布异常, topology={}, publisher={}, error={}",
                            topologyName, publisher.getClass().getSimpleName(), e.getMessage());
                }
            }
            if (successCount == 0) {
                throw new IllegalStateException("扇出发布全部失败: " + topologyName);
            }
            if (successCount < publishers.size()) {
                log.warn("[MultiMQTopology] 扇出发布部分失败, topology={}, success={}/{}",
                        topologyName, successCount, publishers.size());
            }
        }

        /**
         * 主备模式发布：优先主 MQ，失败时降级到备 MQ
         */
        private void publishPrimaryBackup(String message) {
            for (int i = 0; i < publishers.size(); i++) {
                IMessagePublisher publisher = publishers.get(i);
                try {
                    publisher.publish(message);
                    if (i > 0) {
                        log.info("[MultiMQTopology] 主备降级发布成功, topology={}, 使用第 {} 个 MQ",
                                topologyName, i + 1);
                    }
                    return;
                } catch (Exception e) {
                    log.warn("[MultiMQTopology] 主备发布异常, topology={}, 第 {} 个 MQ, error={}",
                            topologyName, i + 1, e.getMessage());
                }
            }
            throw new IllegalStateException("主备发布全部失败: " + topologyName);
        }
    }

    /**
     * 聚合模式的订阅者内部类
     *
     * <p>从多个 MQ 消费消息，统一路由到同一个 handler 处理。
     */
    private static class AggregationSubscriber implements IMessageSubscriber {

        private final String topologyName;
        private final List<IMessageSubscriber> subscribers;

        AggregationSubscriber(String topologyName, List<IMessageSubscriber> subscribers) {
            this.topologyName = topologyName;
            this.subscribers = subscribers;
        }

        @Override
        public String subscribe() {
            // 从第一个订阅者拉取
            for (IMessageSubscriber subscriber : subscribers) {
                try {
                    String result = subscriber.subscribe();
                    if (result != null) {
                        return result;
                    }
                } catch (Exception e) {
                    log.warn("[MultiMQTopology] 聚合拉取异常, topology={}, error={}", topologyName, e.getMessage());
                }
            }
            return null;
        }

        @Override
        public String subscribeAsync(IMessageHandler handler) {
            List<String> consumerIds = new ArrayList<>();
            for (IMessageSubscriber subscriber : subscribers) {
                try {
                    String consumerId = subscriber.subscribeAsync(handler);
                    consumerIds.add(consumerId);
                } catch (Exception e) {
                    log.warn("[MultiMQTopology] 聚合订阅异常, topology={}, subscriber={}, error={}",
                            topologyName, subscriber.getClass().getSimpleName(), e.getMessage());
                }
            }
            if (consumerIds.isEmpty()) {
                throw new IllegalStateException("聚合订阅全部失败: " + topologyName);
            }
            return "aggregation-" + topologyName + "-" + consumerIds.size();
        }

        @Override
        public void stop() {
            for (IMessageSubscriber subscriber : subscribers) {
                try {
                    subscriber.stop();
                } catch (Exception e) {
                    log.warn("[MultiMQTopology] 聚合停止订阅异常, topology={}, error={}",
                            topologyName, e.getMessage());
                }
            }
        }
    }
}
