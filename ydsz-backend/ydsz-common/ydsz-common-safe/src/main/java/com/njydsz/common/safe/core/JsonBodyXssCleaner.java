package com.njydsz.common.safe.core;

import java.util.Iterator;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.json.tree.ArrayNode;
import com.njydsz.common.json.tree.JsonNode;
import com.njydsz.common.json.tree.ObjectNode;
import com.njydsz.common.json.tree.TextNode;
import com.njydsz.common.safe.xss.EscapeUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * JSON Body XSS 清理器
 *
 * <p>递归遍历 JSON 对象的所有字符串值，使用 EscapeUtils 清理潜在的 XSS 脚本。
 * 基于 YdszJson {@link JsonNode} 实现，与 YdszJson 引擎保持一致。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class JsonBodyXssCleaner {

    /**
     * 清理 JSON 字符串中的 XSS 内容
     *
     * @param json JSON 字符串
     * @return 清理后的 JSON 字符串
     */
    public String clean(String json) {
        if (json == null || json.isBlank()) {
            return json;
        }
        try {
            JsonNode parsed = YdszJson.readTree(json);
            JsonNode cleaned = cleanNode(parsed);
            return YdszJson.toJson(cleaned);
        } catch (Exception e) {
            log.debug("[JsonBodyXssCleaner] JSON解析失败，返回原始字符串: {}", e.getMessage());
            return json;
        }
    }

    private JsonNode cleanNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return node;
        }
        if (node.isTextual()) {
            return new TextNode(cleanString(node.asText()));
        }
        if (node.isObject()) {
            ObjectNode cleaned = new ObjectNode();
            Iterator<String> fieldNames = node.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                cleaned.put(fieldName, cleanNode(node.get(fieldName)));
            }
            return cleaned;
        }
        if (node.isArray()) {
            ArrayNode cleaned = new ArrayNode();
            Iterator<JsonNode> elements = node.elements();
            while (elements.hasNext()) {
                JsonNode item = elements.next();
                cleaned.add(cleanNode(item));
            }
            return cleaned;
        }
        return node;
    }

    private String cleanString(String value) {
        String cleaned = EscapeUtils.clean(value);
        if (!cleaned.equals(value)) {
            return value.replace("<", "&lt;").replace(">", "&gt;")
                    .replace("javascript:", "")
                    .replace("vbscript:", "")
                    .replace("onerror=", "")
                    .replace("onload=", "");
        }
        return value;
    }
}
