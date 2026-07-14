package com.njydsz.pmis.common.queue.mq.rocket;

import org.apache.rocketmq.client.producer.DefaultMQProducer;

import com.njydsz.pmis.common.exception.custom.BusinessException;
import com.njydsz.pmis.common.queue.queue.AbstractMessageQueue;
import com.njydsz.pmis.common.queue.service.IMessagePublisher;
import com.njydsz.pmis.common.queue.service.IMessageSubscriber;

import lombok.extern.slf4j.Slf4j;

/**
 * RocketMQ 消息队列
 *
 * <p>RocketMQ 是阿里巴巴开源的分布式消息中间件，具有高可用、高可靠等特点。
 * 支持事务消息、顺序消息、延迟消息等高级特性。
 *
 * <p><b>技术特点：</b>
 * <ul>
 *   <li>事务消息：支持半消息机制，实现分布式事务</li>
 *   <li>延迟消息：支持指定延迟时间发送消息</li>
 *   <li>顺序消息：保证同一分区消息的消费顺序</li>
 *   <li>消息回溯：支持从指定时间重新消费</li>
 *   <li>消费组：支持多个消费者组成消费组协同消费</li>
 * </ul>
 *
 * <p><b>适用场景：</b>
 * <ul>
 *   <li>分布式事务：如订单处理、支付流程</li>
 *   <li>延迟任务：如订单超时取消、延迟发货</li>
 *   <li>异步处理：如注册通知、积分计算</li>
 *   <li>系统解耦：如库存同步、物流通知</li>
 * </ul>
 *
 * <p><b>与 Kafka 对比：</b>
 * <table border="1">
 *   <tr><th>特性</th><th>RocketMQ</th><th>Kafka</th></tr>
 *   <tr><td>事务消息</td><td>支持</td><td>不支持</td></tr>
 *   <tr><td>延迟消息</td><td>支持</td><td>不支持</td></tr>
 *   <tr><td>顺序消息</td><td>支持</td><td>支持</td></tr>
 *   <tr><td>消息回溯</td><td>支持</td><td>支持</td></tr>
 *   <tr><td>吞吐量</td><td>高</td><td>极高</td></tr>
 * </table>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * IMessageQueue queue = messageQueueProvider.createMessageQueue(QueueType.rocket);
 * IMessagePublisher publisher = queue.createPublisher("my-topic");
 * IMessageSubscriber subscriber = queue.createSubscriber("my-topic");
 *
 * publisher.publish("Hello RocketMQ");
 *
 * subscriber.subscribeAsync(message -> {
 *     log.info("Received: {}", message.getBody());
 * });
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * @since 1.0.0
 */
@Slf4j
public class RocketMQ extends AbstractMessageQueue {

    private final RocketMQProperties properties;

    public RocketMQ(RocketMQProperties properties) {
        super("RocketMQ");
        if (properties == null) {
            throw BusinessException.builder().key("RocketMQ 配置不能为空").build();
        }
        this.properties = properties;
        validateConnection();
        log.info("[RocketMQ] 初始化成功，namesrvAddr={}", properties.resolvedNamesrvAddr());
    }

    @Override
    public IMessagePublisher createPublisher(String channel) {
        checkNotClosed();
        if (channel == null || channel.isEmpty()) {
            throw BusinessException.builder().key("主题名称不能为空").build();
        }
        return new RocketMQPublisher(properties, channel);
    }

    @Override
    public IMessageSubscriber createSubscriber(String channel) {
        checkNotClosed();
        if (channel == null || channel.isEmpty()) {
            throw BusinessException.builder().key("主题名称不能为空").build();
        }
        return new RocketMQSubscriber(properties, channel);
    }

    @Override
    protected void doClose() {
    }

    private void validateConnection() {
        try {
            DefaultMQProducer probe = new DefaultMQProducer("rocketmq-connectivity-check");
            probe.setNamesrvAddr(properties.resolvedNamesrvAddr());
            probe.setSendMsgTimeout(3000);
            probe.start();
            probe.shutdown();
            log.debug("[RocketMQ] 连接验证成功");
        } catch (Exception e) {
            log.error("[RocketMQ] 连接验证失败，namesrvAddr={}", properties.resolvedNamesrvAddr(), e);
            throw BusinessException.builder().key("RocketMQ 连接失败，请检查配置：" + e.getMessage()).build();
        }
    }
}
