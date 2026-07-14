package com.njydsz.pmis.system.server.util;

import java.util.*;

import com.njydsz.pmis.common.json.Json;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 字段级差异计算工具。
 * 比较两个对象的 JSON 表示，生成字段级 diff。
 */
public class DiffCalculator {

    private static final Logger log = LoggerFactory.getLogger(DiffCalculator.class);
    // Json as JSON engine

    /**
     * 计算两个 JSON 对象的字段级差异。
     *
     * @param beforeJson 变更前 JSON
     * @param afterJson 变更后 JSON
     * @return 差异列表
     */
    public static List<FieldDiff> calculateDiff(String beforeJson, String afterJson) {
        if (beforeJson == null && afterJson == null) {
            return Collections.emptyList();
        }
        Map<String, Object> before = parseJson(beforeJson);
        Map<String, Object> after = parseJson(afterJson);
        List<FieldDiff> diffs = new ArrayList<>();
        Set<String> allKeys = new LinkedHashSet<>();
        if (before != null) allKeys.addAll(before.keySet());
        if (after != null) allKeys.addAll(after.keySet());
        for (String key : allKeys) {
            Object oldVal = before != null ? before.get(key) : null;
            Object newVal = after != null ? after.get(key) : null;
            if (!Objects.equals(oldVal, newVal)) {
                String type;
                if (oldVal == null) {
                    type = "ADD";
                } else if (newVal == null) {
                    type = "DELETE";
                } else {
                    type = "MODIFY";
                }
                diffs.add(new FieldDiff(key, String.valueOf(oldVal), String.valueOf(newVal), type));
            }
        }
        return diffs;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return Json.toObject(json, Map.class);
        } catch (Exception e) {
            log.warn("[DiffCalculator] JSON 解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 字段差异记录。
     */
    public static class FieldDiff {
        private final String field;
        private final String oldValue;
        private final String newValue;
        private final String changeType; // ADD / DELETE / MODIFY

        public FieldDiff(String field, String oldValue, String newValue, String changeType) {
            this.field = field;
            this.oldValue = oldValue;
            this.newValue = newValue;
            this.changeType = changeType;
        }

        public String getField() { return field; }
        public String getOldValue() { return oldValue; }
        public String getNewValue() { return newValue; }
        public String getChangeType() { return changeType; }
    }
}
