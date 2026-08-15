package com.njydsz.common.queue.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.njydsz.common.queue.enums.QueueType;

/**
 * 消息队列监听器注解
 *
 * <p>标注在 Spring Bean 的方法上，自动将该方法注册为指定队列的消费者。
 * 支持按队列类型、消费组、并发度等参数配置监听行为。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * @Component
 * public class OrderMessageListener {
 *
 *     @QueueListener(topic = "order-events", queueType = QueueType.STREAM)
 *     public void onOrderEvent(QueueMessage message) {
 *         log.info("收到订单消息: {}", message.getBody());
 *         processOrder(message);
 *     }
 *
 *     @QueueListener(topic = "notifications", queueType = QueueType.PUBSUB, concurrency = 4)
 *     public void onNotification(String payload) {
 *         log.info("收到通知: {}", payload);
 *     }
 * }
 * }</pre>
 *
 * <p><b>方法签名要求：</b>
 * <ul>
 *   <li>参数为 {@link com.njydsz.common.queue.domain.QueueMessage}：获取完整消息（含 headers、traceId）</li>
 *   <li>参数为 {@code String}：直接获取消息体字符串</li>
 *   <li>参数为 {@code Map<String, Object>}：获取消息体反序列化后的 Map</li>
 *   <li>无参数：仅触发回调（不推荐，丢失消息内容）</li>
 * </ul>
 *
 * <p><b>注意事项：</b>
 * <ul>
 *   <li>方法必须为 public</li>
 *   <li>标注方法的 Bean 必须被 Spring 管理</li>
 *   <li>如果方法抛出异常，消息可能被重试（取决于队列类型和配置）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface QueueListener {

    /**
     * 监听的队列主题（topic/channel）
     *
     * @return 主题名称
     */
    String topic();

    /**
     * 队列类型
     *
     * @return 队列类型枚举
     */
    QueueType queueType() default QueueType.STREAM;

    /**
     * 消费组名称（仅 Redis Stream 等支持消费组的队列有效）
     *
     * @return 消费组名称，为空时使用配置默认值
     */
    String consumerGroup() default "";

    /**
     * 消费者名称前缀
     *
     * @return 消费者名称前缀，为空时使用类名+方法名
     */
    String consumerName() default "";

    /**
     * 并发消费者数量
     *
     * <p>指定同一消费者组内启动多少个并发消费者实例。
     * 增加并发可提升消费吞吐量，但可能影响顺序性。
     *
     * @return 并发数，默认为 1
     */
    int concurrency() default 1;

    /**
     * 是否异步消费
     *
     * <p>true 时消息在后台线程池中处理，不阻塞消费循环。
     * false 时消息在消费线程中同步处理，处理完才拉取下一条。
     *
     * @return 是否异步，默认为 true
     */
    boolean async() default true;

    /**
     * 忽略异常（不触发重试）
     *
     * <p>true 时方法抛出的异常仅被记录，不触发消息重试。
     * false 时异常会触发消息重试或进入死信队列。
     *
     * @return 是否忽略异常，默认为 false
     */
    boolean ignoreExceptions() default false;
}
