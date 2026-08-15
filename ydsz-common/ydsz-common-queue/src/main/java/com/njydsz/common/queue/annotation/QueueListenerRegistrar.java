package com.njydsz.common.queue.annotation;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;

import com.njydsz.common.queue.domain.QueueMessage;
import com.njydsz.common.queue.queue.IMessageQueue;
import com.njydsz.common.queue.queue.IMessageQueueProvider;
import com.njydsz.common.queue.service.IMessageHandler;
import com.njydsz.common.queue.service.IMessageSubscriber;
import com.njydsz.common.queue.service.impl.MethodMessageHandler;

import lombok.extern.slf4j.Slf4j;

/**
 * 消息监听器注解处理器
 *
 * <p>扫描所有 Spring Bean 中标注了 {@link QueueListener} 的方法，
 * 自动创建对应的 {@link IMessageSubscriber} 并注册为消费者。
 *
 * <p><b>工作流程：</b>
 * <ol>
 *   <li>在 Spring 容器初始化单例阶段触发扫描</li>
 *   <li>查找所有 @QueueListener 标注的方法</li>
 *   <li>为每个方法创建 MethodMessageHandler 包装</li>
 *   <li>按配置创建 IMessageSubscriber 并启动消费</li>
 * </ol>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class QueueListenerRegistrar implements SmartInitializingSingleton {

    private final ApplicationContext applicationContext;
    private final IMessageQueueProvider queueProvider;
    private final Map<String, IMessageSubscriber> activeSubscribers = new ConcurrentHashMap<>();

    public QueueListenerRegistrar(ApplicationContext applicationContext, IMessageQueueProvider queueProvider) {
        this.applicationContext = applicationContext;
        this.queueProvider = queueProvider;
    }

    @Override
    public void afterSingletonsInstantiated() {
        scanAndRegisterListeners();
    }

    /**
     * 扫描并注册所有 @QueueListener
     */
    private void scanAndRegisterListeners() {
        Map<Method, QueueListener> listenerMethods = findListenerMethods();
        if (listenerMethods.isEmpty()) {
            log.info("[QueueListener] 未发现 @QueueListener 标注的方法");
            return;
        }
        log.info("[QueueListener] 发现 {} 个 @QueueListener 方法", listenerMethods.size());

        for (Map.Entry<Method, QueueListener> entry : listenerMethods.entrySet()) {
            Method method = entry.getKey();
            QueueListener annotation = entry.getValue();
            registerListener(method, annotation);
        }
    }

    /**
     * 查找所有标注 @QueueListener 的方法
     */
    private Map<Method, QueueListener> findListenerMethods() {
        Map<Method, QueueListener> result = new ConcurrentHashMap<>();
        String[] beanNames = applicationContext.getBeanNamesForAnnotation(org.springframework.stereotype.Component.class);
        for (String beanName : beanNames) {
            try {
                Object bean = applicationContext.getBean(beanName);
                Class<?> beanClass = bean.getClass();
                Map<Method, QueueListener> methods = MethodIntrospector.selectMethods(beanClass,
                        (MethodIntrospector.MetadataLookup<QueueListener>) method ->
                                AnnotatedElementUtils.findMergedAnnotation(method, QueueListener.class));
                methods.forEach((method, annotation) -> {
                    method.setAccessible(true);
                    result.put(method, annotation);
                });
            } catch (Exception e) {
                log.debug("[QueueListener] 扫描 Bean {} 异常: {}", beanName, e.getMessage());
            }
        }
        // 也扫描 Service, Controller 等派生注解
        scanAdditionalStereotypes(result);
        return result;
    }

    /**
     * 扫描额外的 Spring 派生注解
     */
    private void scanAdditionalStereotypes(Map<Method, QueueListener> result) {
        String[] stereotypes = {"Service", "Controller", "RestController", "Configuration"};
        for (String stereo : stereotypes) {
            try {
                String[] beanNames = applicationContext.getBeanNamesForAnnotation(
                        (Class<? extends java.lang.annotation.Annotation>)
                                Class.forName("org.springframework.stereotype." + stereo));
                for (String beanName : beanNames) {
                    try {
                        Object bean = applicationContext.getBean(beanName);
                        Class<?> beanClass = bean.getClass();
                        MethodIntrospector.selectMethods(beanClass,
                                        (MethodIntrospector.MetadataLookup<QueueListener>) method ->
                                                AnnotatedElementUtils.findMergedAnnotation(method, QueueListener.class))
                                .forEach((method, annotation) -> {
                                    if (!result.containsKey(method)) {
                                        method.setAccessible(true);
                                        result.put(method, annotation);
                                    }
                                });
                    } catch (Exception e) {
                        log.debug("[QueueListener] 扫描 {} Bean {} 异常: {}", stereo, beanName, e.getMessage());
                    }
                }
            } catch (ClassNotFoundException e) {
                log.debug("[QueueListener] 注解类不存在: {}", stereo);
            }
        }
    }

    /**
     * 注册单个 @QueueListener
     */
    private void registerListener(Method method, QueueListener annotation) {
        try {
            String topic = annotation.topic();
            String listenerId = buildListenerId(method, annotation);

            // 创建 queue
            IMessageQueue queue = queueProvider.createMessageQueue(annotation.queueType());
            IMessageSubscriber subscriber = queue.createSubscriber(topic);

            // 创建方法调用处理器
            Object bean = getBeanForMethod(method);
            IMessageHandler handler = new MethodMessageHandler(bean, method, annotation.ignoreExceptions());

            // 启动消费
            String consumerId;
            if (annotation.async()) {
                consumerId = subscriber.subscribeAsync(handler);
            } else {
                // 同步模式：在后台线程中循环拉取
                consumerId = listenerId;
                startSyncPolling(subscriber, handler);
            }

            activeSubscribers.put(listenerId, subscriber);

            log.info("[QueueListener] 注册成功: id={}, topic={}, type={}, method={}, concurrency={}",
                    listenerId, topic, annotation.queueType(),
                    method.getDeclaringClass().getSimpleName() + "." + method.getName(),
                    annotation.concurrency());
        } catch (Exception e) {
            log.error("[QueueListener] 注册失败: method={}, error={}", method, e.getMessage(), e);
        }
    }

    /**
     * 获取方法所属 Bean
     */
    private Object getBeanForMethod(Method method) {
        Class<?> declaringClass = method.getDeclaringClass();
        String[] beanNames = applicationContext.getBeanNamesForType(declaringClass);
        if (beanNames.length == 0) {
            throw new IllegalStateException("未找到 Bean: " + declaringClass.getName());
        }
        return applicationContext.getBean(beanNames[0]);
    }

    /**
     * 同步轮询模式启动
     */
    private void startSyncPolling(IMessageSubscriber subscriber, IMessageHandler handler) {
        Thread pollThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    if (subscriber instanceof com.njydsz.common.queue.service.IMessageSubscriber) {
                        QueueMessage message = MessageSubscriberHelper.subscribeMessage(subscriber);
                        if (message != null) {
                            handler.onMessage(message);
                        }
                    }
                } catch (Exception e) {
                    log.warn("[QueueListener] 同步消费异常: {}", e.getMessage());
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }, "ydsz-queue-sync-poll-" + System.nanoTime());
        pollThread.setDaemon(true);
        pollThread.start();
    }

    /**
     * 构建监听器 ID
     */
    private String buildListenerId(Method method, QueueListener annotation) {
        String consumerName = annotation.consumerName();
        if (consumerName.isEmpty()) {
            consumerName = method.getDeclaringClass().getSimpleName() + "-" + method.getName();
        }
        return annotation.topic() + "-" + consumerName;
    }

    /**
     * 获取活跃订阅者数量（用于监控）
     *
     * @return 活跃订阅者数量
     */
    public int getActiveListenerCount() {
        return activeSubscribers.size();
    }

    /**
     * 停止所有监听器
     */
    public void stopAll() {
        activeSubscribers.forEach((id, subscriber) -> {
            try {
                subscriber.stop();
            } catch (Exception e) {
                log.warn("[QueueListener] 停止监听器异常: id={}, error={}", id, e.getMessage());
            }
        });
        activeSubscribers.clear();
    }
}
