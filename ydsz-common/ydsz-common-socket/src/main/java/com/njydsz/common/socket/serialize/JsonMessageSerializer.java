package com.njydsz.common.socket.serialize;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.json.YdszJson;

/**
 * JSON 消息序列化器默认实现（P3-5）。
 *
 * <p>使用项目统一的 {@link YdszJson} 引擎进行序列化/反序列化。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class JsonMessageSerializer implements MessageSerializer {

  @Override
  public String serialize(Object payload) {
    if (payload == null) {
      return "{}";
    }
    if (payload instanceof String s) {
      return s;
    }
    try {
      return YdszJson.toJson(payload);
    } catch (Exception e) {
      log.warn("[WS-Serialize] 序列化失败, 降级 toString: {}", e.getMessage());
      return String.valueOf(payload);
    }
  }

  @Override
  public String getName() {
    return "JSON";
  }
}
