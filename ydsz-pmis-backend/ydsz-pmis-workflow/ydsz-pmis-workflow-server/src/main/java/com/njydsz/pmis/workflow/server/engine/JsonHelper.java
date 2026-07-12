package com.njydsz.pmis.workflow.server.engine;

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
     *
     * <p>注：原实现使用 {@code JSON.parseObject(json, Map.class, SupportSmartMatch)}，
     * 会触发 unchecked cast 警告。为保持与历史行为一致（含 SmartMatch 特性），
     * 此处仍保留 fastjson2 直接调用；如不需要 SmartMatch，建议改用
     * {@link com.njydsz.pmis.common.util.JsonUtils#parseMap(String)}。
     *
     * @param json JSON 字符串
     * @return 解析后的 Map；输入为 null/空白时返回 null
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return JSON.parseObject(json, Map.class, JSONReader.Feature.SupportSmartMatch);
    }
}
