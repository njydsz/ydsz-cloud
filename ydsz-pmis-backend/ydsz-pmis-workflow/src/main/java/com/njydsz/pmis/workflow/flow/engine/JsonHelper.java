package com.njydsz.pmis.workflow.flow.engine;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;

import java.util.Map;

/**
 * 工作流引擎 JSON 工具（隔离 fastjson2，便于测试 mock）
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
public final class JsonHelper {

    private JsonHelper() {
    }

    /**
     * 对象 → JSON 字符串
     */
    public static String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        return JSON.toJSONString(obj, JSONWriter.Feature.WriteNulls);
    }

    /**
     * JSON 字符串 → Map
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return JSON.parseObject(json, Map.class, JSONReader.Feature.SupportSmartMatch);
    }
}
