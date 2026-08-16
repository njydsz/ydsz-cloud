package com.njydsz.common.socket.serialize;

/**
 * 消息序列化器接口（P3-5）。
 *
 * <p>抽象消息序列化逻辑，默认使用 {@link JsonMessageSerializer}（JSON）， 业务方可替换为 Protobuf 等其他协议实现。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface MessageSerializer {

  /**
   * 序列化消息内容为字符串。
   *
   * @param payload 消息内容
   * @return 序列化后的字符串
   */
  String serialize(Object payload);

  /**
   * 反序列化消息内容。
   *
   * @param json 序列化后的字符串
   * @param clazz 目标类型
   * @param <T> 目标类型
   * @return 反序列化后的对象
   */

  /**
   * 获取序列化器名称。
   *
   * @return 序列化器名称
   */
  String getName();
}
