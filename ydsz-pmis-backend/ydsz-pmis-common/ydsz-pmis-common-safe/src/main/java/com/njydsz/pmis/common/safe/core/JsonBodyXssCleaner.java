package com.njydsz.pmis.common.safe.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.njydsz.pmis.common.safe.xss.EscapeUtils;
import com.njydsz.pmis.common.util.json.JsonUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.Iterator;
import java.util.Map;

/**
 * JSON Body XSS 清理器
 *
 * <p>递归遍历 JSON 对象的所有字符串值，使用 EscapeUtils 清理潜在的 XSS 脚本。
 * 基于 Jackson {@link JsonNode} 实现，与 Jackson JSON 引擎保持一致。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 4.0.0
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
            ObjectMapper mapper = JsonUtils.getMapper();
            JsonNode parsed = mapper.readTree(json);
            JsonNode cleaned = cleanNode(parsed);
            return mapper.writeValueAsString(cleaned);
        } catch (Exception e) {
            // 如果 JSON 解析失败，返回原始字符串
            log.debug("[JsonBodyXssCleaner] JSON解析失败，返回原始字符串: {}", e.getMessage());
            return json;
        }
    }

    @SuppressWarnings("deprecation")
    private JsonNode cleanNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return node;
        }
        if (node.isTextual()) {
            return new TextNode(cleanString(node.asText()));
        }
        if (node.isObject()) {
            ObjectNode cleaned = JsonUtils.getMapper().createObjectNode();
            for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> entry = it.next();
                cleaned.set(entry.getKey(), cleanNode(entry.getValue()));
            }
            return cleaned;
        }
        if (node.isArray()) {
            ArrayNode cleaned = JsonUtils.getMapper().createArrayNode();
            for (JsonNode item : node) {
                cleaned.add(cleanNode(item));
            }
            return cleaned;
        }
        return node;
    }

    private String cleanString(String value) {
        String cleaned = EscapeUtils.clean(value);
        // 如果清理后内容变化，说明有潜在 XSS
        if (!cleaned.equals(value)) {
            // 进一步转义危险字符
            return value.replace("<", "&lt;").replace(">", "&gt;")
                    .replace("javascript:", "")
                    .replace("vbscript:", "")
                    .replace("onerror=", "")
                    .replace("onload=", "");
        }
        return value;
    }
}
