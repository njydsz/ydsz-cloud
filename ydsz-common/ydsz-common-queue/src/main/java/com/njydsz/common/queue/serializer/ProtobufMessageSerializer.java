package com.njydsz.common.queue.serializer;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.queue.domain.QueueMessage;

/**
 * Protobuf 消息序列化器（可选实现）
 *
 * <p>基于 Google Protocol Buffers 实现二进制格式的消息序列化和反序列化。
 *
 * <p>通过反射检测 protobuf 运行时是否可用，避免编译期硬依赖。 当 protobuf-java 不在 classpath 中时，调用序列化方法将抛出
 * UnsupportedOperationException。
 *
 * <p><b>使用前提：</b>
 *
 * <ul>
 *   <li>需要引入 protobuf-java 依赖
 *   <li>需要定义 QueueMessage 对应的 .proto 文件并生成 Java 类
 *   <li>需要实现具体的 proto 转换逻辑
 * </ul>
 *
 * <p><b>性能优势（对比 JSON）：</b>
 *
 * <ul>
 *   <li>序列化后体积减少 50%-80%
 *   <li>编解码速度提升 5-10 倍
 *   <li>适合高吞吐、低延迟场景
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class ProtobufMessageSerializer implements MessageSerializer {

  /** protobuf Message 类名（反射检测） */
  private static final String PROTOBUF_MESSAGE_CLASS = "com.google.protobuf.Message";

  /** protobuf 是否可用的缓存标记（null=未检查，TRUE=可用，FALSE=不可用） */
  private static final AtomicReference<Boolean> PROTOBUF_AVAILABLE = new AtomicReference<>();

  /**
   * 检测 protobuf 运行时是否可用
   *
   * @return true 如果 protobuf-java 在 classpath 中
   */
  public static boolean isProtobufAvailable() {
    Boolean result = PROTOBUF_AVAILABLE.get();
    if (result != null) {
      return result;
    }
    try {
      Class.forName(PROTOBUF_MESSAGE_CLASS);
      if (PROTOBUF_AVAILABLE.compareAndSet(null, Boolean.TRUE)) {
        return true;
      }
    } catch (ClassNotFoundException e) {
      PROTOBUF_AVAILABLE.compareAndSet(null, Boolean.FALSE);
      log.debug("protobuf-java 不可用，ProtobufMessageSerializer 将不可用");
    }
    return PROTOBUF_AVAILABLE.get();
  }

  @Override
  public String serialize(QueueMessage message) throws SerializationException {
    if (!isProtobufAvailable()) {
      throw new UnsupportedOperationException("protobuf-java 不可用，请在 pom.xml 中引入 protobuf-java 依赖");
    }
    if (message == null) {
      return null;
    }
    // 当 protobuf 可用时的序列化逻辑
    // 实际使用时需要通过 proto 生成的 Builder 构建消息并 toByteString
    throw new UnsupportedOperationException(
        "Protobuf 序列化需要先定义 .proto 文件并生成对应的 Java 类，"
            + "然后实现 QueueMessage 与 proto 对象之间的转换逻辑。"
            + "参见 docs/mq-protobuf-integration.md 获取详细配置指南。");
  }

  @Override
  public QueueMessage deserialize(String payload) throws SerializationException {
    if (!isProtobufAvailable()) {
      throw new UnsupportedOperationException("protobuf-java 不可用，请在 pom.xml 中引入 protobuf-java 依赖");
    }
    if (payload == null || payload.isEmpty()) {
      return null;
    }
    // 当 protobuf 可用时的反序列化逻辑
    throw new UnsupportedOperationException(
        "Protobuf 反序列化需要先定义 .proto 文件并生成对应的 Java 类，"
            + "然后实现 proto 对象与 QueueMessage 之间的转换逻辑。"
            + "参见 docs/mq-protobuf-integration.md 获取详细配置指南。");
  }

  @Override
  public String getFormatName() {
    return "protobuf";
  }

  /**
   * 通过反射调用 protobuf 方法（可供后续扩展使用）
   *
   * @param target 目标对象
   * @param methodName 方法名
   * @param args 参数
   * @return 方法返回值
   */
  protected Object invokeProtobufMethod(Object target, String methodName, Object... args) {
    try {
      Class<?>[] paramTypes = new Class<?>[args.length];
      for (int i = 0; i < args.length; i++) {
        paramTypes[i] = args[i].getClass();
      }
      Method method = target.getClass().getMethod(methodName, paramTypes);
      return method.invoke(target, args);
    } catch (Exception e) {
      throw new SerializationException("反射调用 protobuf 方法失败: " + methodName, e);
    }
  }
}
