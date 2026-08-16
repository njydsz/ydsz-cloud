package com.njydsz.common.queue.serializer;

import com.njydsz.common.queue.domain.QueueMessage;

/**
 * 消息序列化器接口
 *
 * <p>定义消息序列化和反序列化的统一契约，支持 JSON、Protobuf 等多种序列化格式。
 *
 * <p>通过策略模式实现可插拔的序列化机制，业务代码可按需选择合适的序列化格式：
 *
 * <ul>
 *   <li>JSON：默认格式，可读性好，兼容性强
 *   <li>Protobuf：高性能二进制格式，体积小、编解码快，适合高吞吐场景
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * MessageSerializer serializer = SerializerFactory.getSerializer("json");
 * String payload = serializer.serialize(message);
 * QueueMessage restored = serializer.deserialize(payload);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface MessageSerializer {

  /**
   * 将消息对象序列化为字符串
   *
   * @param message 消息对象
   * @return 序列化后的字符串 payload
   * @throws SerializationException 如果序列化失败
   */
  String serialize(QueueMessage message) throws SerializationException;

  /**
   * 将字符串反序列化为消息对象
   *
   * @param payload 序列化后的字符串
   * @return 反序列化后的消息对象，输入为空时返回 null
   * @throws SerializationException 如果反序列化失败
   */
  QueueMessage deserialize(String payload) throws SerializationException;

  /**
   * 获取序列化格式名称
   *
   * @return 格式名称（如 "json"、"protobuf"）
   */
  String getFormatName();
}
