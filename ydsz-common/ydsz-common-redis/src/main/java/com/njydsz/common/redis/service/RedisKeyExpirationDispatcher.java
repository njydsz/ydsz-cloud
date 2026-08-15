package com.njydsz.common.redis.service;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import com.njydsz.common.redis.annotation.RedisKeyExpireListener;
import com.njydsz.common.redis.config.RedisProperties;
import com.njydsz.common.redis.event.RedisKeyExpirationEvent;

/**
 * Redis Key 过期事件监听调度器
 *
 * <p>扫描所有 Spring Bean 中标注了 {@link RedisKeyExpireListener} 的方法，
 * 注册 Redis Keyspace Notification 订阅，当匹配的 Key 过期时自动调用对应方法。
 *
 * <p><b>工作原理：</b>
 * <ol>
 *   <li>扫描 Spring 容器中所有 Bean 的所有方法</li>
 *   <li>找到标注了 {@link RedisKeyExpireListener} 的方法</li>
 *   <li>根据注解的 {@code dbIndex} 和 {@code keyPattern} 创建订阅</li>
 *   <li>收到过期事件时，匹配 keyPattern 并调用对应的处理方法</li>
 * </ol>
 *
 * <p><b>注意：</b>需要 Redis 服务端配置 {@code notify-keyspace-events Ex}，
 * 否则无法接收到过期事件。
 *
 * <p><b>迁移说明：</b>自 v1.1.0 起标记废弃，计划 v2.0.0 移除。
 * 当前无业务消费方。如需监听 Key 过期事件，推荐直接在业务模块中实现 {@code MessageListener}。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @deprecated 自 v1.1.0 起无消费方，计划 v2.0.0 移除。替代方案：业务模块实现 MessageListener。
 */
@Slf4j
@Deprecated(since = "1.1.0", forRemoval = true)
public class RedisKeyExpirationDispatcher implements ApplicationContextAware, SmartInitializingSingleton, DisposableBean {

    /** 过期事件通道前缀 */
    private static final String KEYEVENT_EXPIRED_PREFIX = "__keyevent@";
    /** 过期事件通道后缀 */
    private static final String KEYEVENT_EXPIRED_SUFFIX = "__:expired";
    /** 全局模式：监听所有数据库的过期事件 */
    private static final String TOPIC_ALL_DB = KEYEVENT_EXPIRED_PREFIX + "*" + KEYEVENT_EXPIRED_SUFFIX;
    /** 异步执行线程池名称前缀 */
    private static final String THREAD_POOL_NAME = "redis-key-expire-";

    private ApplicationContext applicationContext;
    private final RedisMessageListenerContainer listenerContainer;
    private final RedisProperties redisProperties;
    private final ExecutorService executorService;
    private final List<ExpireListenerRegistration> registrations = new ArrayList<>();

    /**
     * 过期监听器注册信息
     */
    private record ExpireListenerRegistration(
            Object bean,
            Method method,
            String keyPattern,
            int dbIndex,
            boolean acceptStringParam,
            boolean acceptEventParam) {
    }

    public RedisKeyExpirationDispatcher(RedisMessageListenerContainer listenerContainer,
                                        RedisProperties redisProperties) {
        this.listenerContainer = Objects.requireNonNull(listenerContainer, "listenerContainer 不能为 null");
        this.redisProperties = redisProperties;
        this.executorService = new ThreadPoolExecutor(
                2, 2, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(1024),
                r -> {
                    Thread t = new Thread(r, THREAD_POOL_NAME + "handler");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    @Override
    public void afterSingletonsInstantiated() {
        init();
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * 初始化：扫描所有 Bean 并注册监听器
     */
    public void init() {
        if (applicationContext == null) {
            log.warn("【RedisKeyExpire】ApplicationContext 未设置，跳过过期事件监听初始化");
            return;
        }

        String[] beanNames = applicationContext.getBeanDefinitionNames();
        for (String beanName : beanNames) {
            try {
                Object bean = applicationContext.getBean(beanName);
                scanBeanForListeners(bean);
            } catch (Exception e) {
                log.debug("【RedisKeyExpire】扫描 Bean 失败 | beanName={}", beanName, e);
            }
        }

        if (registrations.isEmpty()) {
            log.info("【RedisKeyExpire】未发现 @RedisKeyExpireListener 标注的方法，跳过订阅");
            return;
        }

        // 注册全局过期事件监听器
        registerGlobalListener();
        log.info("【RedisKeyExpire】过期事件监听初始化完成 | listenerCount={}", registrations.size());
    }

    /**
     * 扫描 Bean 中标注了 @RedisKeyExpireListener 的方法
     */
    private void scanBeanForListeners(Object bean) {
        Class<?> targetClass = bean.getClass();
        for (Method method : targetClass.getDeclaredMethods()) {
            RedisKeyExpireListener annotation =
                    AnnotatedElementUtils.findMergedAnnotation(method, RedisKeyExpireListener.class);
            if (annotation == null) {
                continue;
            }

            validateMethodSignature(method);

            boolean acceptStringParam = method.getParameterCount() == 1
                    && method.getParameterTypes()[0] == String.class;
            boolean acceptEventParam = method.getParameterCount() == 1
                    && method.getParameterTypes()[0] == RedisKeyExpirationEvent.class;

            if (!acceptStringParam && !acceptEventParam) {
                throw new IllegalArgumentException(String.format(
                        "@RedisKeyExpireListener 标注的方法签名不合法：%s.%s，" +
                                "必须为 void method(String) 或 void method(RedisKeyExpirationEvent)",
                        targetClass.getName(), method.getName()));
            }

            String keyPattern = resolveKeyPattern(annotation);
            registrations.add(new ExpireListenerRegistration(
                    bean, method, keyPattern, annotation.dbIndex(),
                    acceptStringParam, acceptEventParam));

            log.info("【RedisKeyExpire】注册过期监听 | bean={} | method={} | pattern={} | dbIndex={}",
                    targetClass.getSimpleName(), method.getName(), keyPattern, annotation.dbIndex());
        }
    }

    /**
     * 解析 keyPattern（支持 SpEL 占位符）
     */
    private String resolveKeyPattern(RedisKeyExpireListener annotation) {
        String pattern = annotation.keyPattern();
        if (pattern == null || pattern.isEmpty()) {
            return "*";
        }
        // 简单占位符解析：${...} 从 RedisProperties 或环境变量中查找
        if (annotation.spelEnabled() && pattern.contains("${") && pattern.contains("}")) {
            // 简化实现：直接返回原值，由 Spring 的 @Value 处理
            // 实际 SpEL 解析需要 ApplicationContext.resolveEmbeddedValue
            if (applicationContext != null) {
                try {
                    String resolved = applicationContext.getEnvironment().resolvePlaceholders(pattern);
                    if (resolved != null) {
                        return resolved;
                    }
                } catch (Exception e) {
                    log.debug("【RedisKeyExpire】SpEL 解析失败，使用原始值 | pattern={}", pattern);
                }
            }
        }
        return pattern;
    }

    /**
     * 验证方法签名
     */
    private void validateMethodSignature(Method method) {
        if (method.getParameterCount() != 1) {
            throw new IllegalArgumentException(String.format(
                    "@RedisKeyExpireListener 标注的方法必须有且仅有 1 个参数：%s.%s",
                    method.getDeclaringClass().getName(), method.getName()));
        }
        Class<?> paramType = method.getParameterTypes()[0];
        if (paramType != String.class && paramType != RedisKeyExpirationEvent.class) {
            throw new IllegalArgumentException(String.format(
                    "@RedisKeyExpireListener 标注的方法参数类型必须是 String 或 RedisKeyExpirationEvent：%s.%s",
                    method.getDeclaringClass().getName(), method.getName()));
        }
    }

    /**
     * 注册全局过期事件监听器
     */
    private void registerGlobalListener() {
        MessageListener listener = this::onMessageReceived;
        // 使用 PatternTopic 监听所有数据库的过期事件
        PatternTopic topic = new PatternTopic(TOPIC_ALL_DB);
        listenerContainer.addMessageListener(listener, topic);
    }

    /**
     * 收到过期事件消息
     */
    private void onMessageReceived(Message message, byte[] pattern) {
        String channel = new String(message.getChannel());
        String expiredKey = new String(message.getBody());

        log.debug("【RedisKeyExpire】收到过期事件 | channel={} | expiredKey={}", channel, expiredKey);

        // 提取数据库索引
        int dbIndex = extractDbIndex(channel);

        // 异步分发到匹配的监听器
        for (ExpireListenerRegistration reg : registrations) {
            if (reg.dbIndex() >= 0 && reg.dbIndex() != dbIndex) {
                continue; // 数据库索引不匹配
            }
            if (matchesPattern(expiredKey, reg.keyPattern())) {
                executorService.submit(() -> dispatchToListener(reg, expiredKey));
            }
        }
    }

    /**
     * 分发事件到具体监听器方法
     */
    private void dispatchToListener(ExpireListenerRegistration reg, String expiredKey) {
        try {
            reg.method().setAccessible(true);
            String businessKey = stripKeyPrefix(expiredKey);
            if (reg.acceptStringParam()) {
                reg.method().invoke(reg.bean(), expiredKey);
            } else if (reg.acceptEventParam()) {
                reg.method().invoke(reg.bean(), new RedisKeyExpirationEvent(expiredKey, businessKey));
            }
        } catch (Exception e) {
            log.error("【RedisKeyExpire】调用过期监听器失败 | method={}.{} | expiredKey={}",
                    reg.method().getDeclaringClass().getSimpleName(),
                    reg.method().getName(), expiredKey, e);
        }
    }

    /**
     * 从 channel 中提取数据库索引
     * channel 格式: __keyevent@N__:expired
     */
    private int extractDbIndex(String channel) {
        try {
            int start = channel.indexOf('@');
            int end = channel.indexOf("__");
            if (start >= 0 && end > start) {
                return Integer.parseInt(channel.substring(start + 1, end));
            }
        } catch (Exception e) {
            log.debug("【RedisKeyExpire】解析数据库索引失败 | channel={}", channel);
        }
        return -1;
    }

    /**
     * 简单的 Ant 风格模式匹配
     *
     * <p>支持 * 匹配任意字符（不含 :），** 匹配任意字符（含 :）
     */
    private boolean matchesPattern(String key, String pattern) {
        if ("*".equals(pattern) || "**".equals(pattern)) {
            return true;
        }
        // 简单实现：将 * 转换为正则的 [^:]*，** 转换为 .*
        String regex = pattern
                .replace(".", "\\.")
                .replace("**", "___DOUBLESTAR___")
                .replace("*", "[^:]*")
                .replace("___DOUBLESTAR___", ".*");
        return key.matches(regex);
    }

    /**
     * 去除 key 前缀
     */
    private String stripKeyPrefix(String key) {
        if (redisProperties != null && redisProperties.getKeyPrefix() != null) {
            String prefix = redisProperties.getKeyPrefix();
            if (!prefix.isEmpty() && key.startsWith(prefix + ":")) {
                return key.substring(prefix.length() + 1);
            }
        }
        return key;
    }

    /**
     * 优雅关闭
     */
    @PreDestroy
    @Override
    public void destroy() {
        executorService.shutdown();
        log.info("【RedisKeyExpire】过期事件调度器已关闭");
    }
}
