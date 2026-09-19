package com.njydsz.common.redis.annotation;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import com.njydsz.common.redis.config.RedisProperties;
import com.njydsz.common.redis.event.RedisKeyExpirationEvent;

/**
 * Redis Key 过期事件监听注册表
 *
 * <p>扫描所有 Spring Bean 中标注了 {@link RedisKeyExpireListener} 的方法，将它们注册为
 * Redis Keyspace Notification 的订阅者。当匹配的 Key 过期时回调对应方法。
 *
 * <p><b>使用前提：</b>Redis 服务端需配置 {@code notify-keyspace-events Ex}（或包含 E 和 x 的组合）。
 *
 * <p><b>方法签名：</b>支持以下两种形式：
 * <pre>{@code
 * // 形式 1：接收原始 key 字符串
 * @RedisKeyExpireListener(keyPattern = "order:lock:*")
 * public void onExpired(String expiredKey) { ... }
 *
 * // 形式 2：接收完整事件对象
 * @RedisKeyExpireListener(keyPattern = "session:*")
 * public void onExpired(RedisKeyExpirationEvent event) { ... }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@RequiredArgsConstructor
public class RedisKeyExpireListenerRegistry implements SmartInitializingSingleton {

  private final ApplicationContext applicationContext;
  private final RedisMessageListenerContainer listenerContainer;
  private final RedisProperties redisProperties;

  /** SpEL 解析器（用于 keyPattern 中的占位符解析） */
  private static final ExpressionParser SPEL_PARSER = new SpelExpressionParser();

  /** 已注册的监听器定义 */
  private final Map<String, ListenerDefinition> listenerDefinitions = new ConcurrentHashMap<>();

  @Override
  public void afterSingletonsInstantiated() {
    if (!redisProperties.getKeyExpiration().isEnabled()) {
      log.info("【RedisKeyExpireListener】Key 过期事件监听已禁用（ydsz.redis.key-expiration.enabled=false），跳过注册");
      return;
    }

    // 扫描所有 Spring Bean，查找标注了 @RedisKeyExpireListener 的方法
    for (Map.Entry<String, Object> entry : applicationContext.getBeansWithAnnotation(org.springframework.stereotype.Component.class).entrySet()) {
      registerBeanListeners(entry.getKey(), entry.getValue());
    }
    // 同时扫描@Service @Repository 等衍生注解
    for (Map.Entry<String, Object> entry : applicationContext.getBeansWithAnnotation(org.springframework.stereotype.Service.class).entrySet()) {
      registerBeanListeners(entry.getKey(), entry.getValue());
    }

    log.info("【RedisKeyExpireListener】共注册 {} 个 Key 过期事件监听方法", listenerDefinitions.size());

    if (!listenerDefinitions.isEmpty()) {
      listenerContainer.addMessageListener(new KeyExpirationDispatcher(), new PatternTopic("__keyevent@*__:expired"));
      log.info("【RedisKeyExpireListener】已订阅 Keyspace Notification（__keyevent@*__:expired）");
    }
  }

  private void registerBeanListeners(String beanName, Object bean) {
    Class<?> targetClass = org.springframework.aop.support.AopUtils.getTargetClass(bean);
    for (Method method : targetClass.getDeclaredMethods()) {
      RedisKeyExpireListener annotation = method.getAnnotation(RedisKeyExpireListener.class);
      if (annotation != null) {
        validateMethodSignature(method);
        String resolvedPattern = resolvePattern(annotation);
        String regKey = beanName + "#" + method.getName() + "@" + resolvedPattern;
        listenerDefinitions.put(
            regKey,
            new ListenerDefinition(bean, method, resolvedPattern, annotation.dbIndex()));
        log.debug(
            "【RedisKeyExpireListener】注册过期事件监听 | bean={} | method={} | pattern={}",
            beanName, method.getName(), resolvedPattern);
      }
    }
  }

  private String resolvePattern(RedisKeyExpireListener annotation) {
    String pattern = annotation.keyPattern();
    if (annotation.spelEnabled() && pattern != null && pattern.startsWith("${") && pattern.endsWith("}")) {
      Expression expr = SPEL_PARSER.parseExpression(pattern);
      String resolved = expr.getValue(applicationContext.getEnvironment(), String.class);
      return resolved != null ? resolved : pattern;
    }
    return pattern;
  }

  private void validateMethodSignature(Method method) {
    Class<?>[] paramTypes = method.getParameterTypes();
    if (paramTypes.length != 1
        && !(paramTypes[0] == String.class || paramTypes[0] == RedisKeyExpirationEvent.class)) {
      throw new IllegalArgumentException(
          String.format(
              "@RedisKeyExpireListener 标注的方法 %s.%s 参数类型必须是 String 或 RedisKeyExpirationEvent",
              method.getDeclaringClass().getName(), method.getName()));
    }
  }

  /** 监听器定义 */
  private record ListenerDefinition(Object bean, Method method, String pattern, int dbIndex) {}

  /** Keyspace Notification 消息分发器 */
  private class KeyExpirationDispatcher implements MessageListener {

    @Override
    public void onMessage(Message message, byte[] pattern) {
      String expiredKey = new String(message.getBody());
      String patternStr = new String(pattern);

      // 提取 db 索引（从 pattern __keyevent@N__:expired）
      int dbIndex = extractDbIndex(patternStr);

      for (ListenerDefinition def : listenerDefinitions.values()) {
        if (def.dbIndex() >= 0 && def.dbIndex() != dbIndex) {
          continue;
        }
        if (matches(expiredKey, def.pattern())) {
          invokeListener(def, expiredKey);
        }
      }
    }

    private void invokeListener(ListenerDefinition def, String expiredKey) {
      try {
        def.method().setAccessible(true);
        if (def.method().getParameterTypes()[0] == RedisKeyExpirationEvent.class) {
          def.method().invoke(def.bean(), new RedisKeyExpirationEvent(expiredKey));
        } else {
          def.method().invoke(def.bean(), expiredKey);
        }
      } catch (Exception e) {
        log.error(
            "【RedisKeyExpireListener】监听方法执行异常 | bean={} | method={} | key={} | cause={}",
            def.bean().getClass().getSimpleName(), def.method().getName(), expiredKey,
            e.getMessage());
      }
    }

    private int extractDbIndex(String pattern) {
      // pattern 形式：__keyevent@N__:expired
      try {
        int start = pattern.indexOf('@');
        int end = pattern.indexOf("__:expired");
        if (start >= 0 && end > start) {
          return Integer.parseInt(pattern.substring(start + 1, end));
        }
      } catch (NumberFormatException ignored) {
        // 解析失败返回 -1（全部数据库）
      }
      return -1;
    }

    /**
     * 简化版 Ant 风格模式匹配
     *
     * <p>支持 {@code *} 匹配任意字符序列（不含冒号分隔符边界），{@code **} 含冒号边界。
     * 由于 expiredKey 格式确定，仅实现基础通配符即可满足需求。
     */
    private boolean matches(String key, String pattern) {
      if (pattern == null || "*".equals(pattern) || "**".equals(pattern)) {
        return true;
      }
      // 将 Ant 风格模式转为正则
      StringBuilder regex = new StringBuilder();
      for (int i = 0; i < pattern.length(); i++) {
        char c = pattern.charAt(i);
        if (c == '*') {
          // 检查是否为 **（双层通配）
          if (i + 1 < pattern.length() && pattern.charAt(i + 1) == '*') {
            regex.append(".*");
            i++; // 跳过第二个 *
          } else {
            regex.append("[^:]*");
          }
        } else if (c == '?') {
          regex.append("[^:]");
        } else if ("+(){}[]^$\\.|".indexOf(c) >= 0) {
          regex.append('\\').append(c);
        } else {
          regex.append(c);
        }
      }
      return key.matches(regex.toString());
    }
  }
}
