package com.njydsz.pmis.common.socket.serialize;

import com.njydsz.pmis.common.json.Json;

import lombok.extern.slf4j.Slf4j;

/**
 * JSON 消息序列化器默认实现（P3-5）。
 *
 * <p>使用项目统一的 {@link Json} 引擎进行序列化/反序列化。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
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
            return Json.toJson(payload);
        } catch (Exception e) {
            log.warn("[WS-Serialize] 序列化失败, 降级 toString: {}", e.getMessage());
            return String.valueOf(payload);
        }
    }

    @Override
    public <T> T deserialize(String json, Class<T> clazz) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return Json.toObject(json, clazz);
        } catch (Exception e) {
            log.warn("[WS-Serialize] 反序列化失败: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public String getName() {
        return "JSON";
    }
}
