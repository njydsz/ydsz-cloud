package com.njydsz.common.queue.serializer;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.queue.domain.QueueMessage;

/**
 * JSON 消息序列化器（默认实现）
 *
 * <p>基于 ydsz-common-json（Fastjson2）实现 JSON 格式的消息序列化和反序列化。
 *
 * <p>作为默认序列化格式，具有以下特点：
 *
 * <ul>
 *   <li>可读性好，便于调试和问题排查
 *   <li>兼容性强，跨语言支持
 *   <li>自描述格式，字段名包含在序列化结果中
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class JsonMessageSerializer implements MessageSerializer {

  @Override
  public String serialize(QueueMessage message) throws SerializationException {
    if (message == null) {
      return null;
    }
    try {
      return YdszJson.toJson(message);
    } catch (Exception e) {
      throw new SerializationException("JSON 序列化失败: " + e.getMessage(), e);
    }
  }

  @Override
  public QueueMessage deserialize(String payload) throws SerializationException {
    if (payload == null || payload.isEmpty()) {
      return null;
    }
    try {
      QueueMessage message = YdszJson.fromJson(payload, QueueMessage.class);
      if (message == null || message.getBody() == null) {
        // 非 JSON 格式或 body 为空，降级为以 payload 为 body
        return QueueMessage.of(payload);
      }
      return message;
    } catch (Exception e) {
      throw new SerializationException("JSON 反序列化失败: " + e.getMessage(), e);
    }
  }

  @Override
  public String getFormatName() {
    return "json";
  }
}
