package com.njydsz.pmis.workflow.server.engine;

import java.util.Map;

import com.njydsz.pmis.common.json.YdszJson;

/**
 * 工作流引擎 JSON 工具（基于 YdszJson 引擎，统一使用 JsonUtils）
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
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
        return YdszJson.toJson(obj);
    }

    /**
     * JSON 字符串 → Map
     *
     * @param json JSON 字符串
     * @return 解析后的 Map；输入为 null/空白时返回 null
     */
    public static Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return YdszJson.parseMap(json);
    }
}
