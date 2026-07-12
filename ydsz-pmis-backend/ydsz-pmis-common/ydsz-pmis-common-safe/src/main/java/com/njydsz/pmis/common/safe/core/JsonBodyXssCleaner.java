package com.njydsz.pmis.common.safe.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.njydsz.pmis.common.safe.xss.EscapeUtils;
import com.njydsz.pmis.common.util.json.JsonUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * JSON Body XSS 娓呯悊鍣? *
 * <p>閫掑綊閬嶅巻 JSON 瀵硅薄鐨勬墍鏈夊瓧绗︿覆鍊硷紝浣跨敤 EscapeUtils 娓呯悊娼滃湪鐨?XSS 鑴氭湰銆? * 鍩轰簬 Jackson {@link JsonNode} 瀹炵幇锛屼笌 Jackson JSON 寮曟搸淇濇寔涓€鑷淬€? *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 4.0.0
 */
@Slf4j
public class JsonBodyXssCleaner {

    /**
     * 娓呯悊 JSON 瀛楃涓蹭腑鐨?XSS 鍐呭
     *
     * @param json JSON 瀛楃涓?     * @return 娓呯悊鍚庣殑 JSON 瀛楃涓?     */
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
            // 濡傛灉 JSON 瑙ｆ瀽澶辫触锛岃繑鍥炲師濮嬪瓧绗︿覆
            log.debug("[JsonBodyXssCleaner] JSON瑙ｆ瀽澶辫触锛岃繑鍥炲師濮嬪瓧绗︿覆: {}", e.getMessage());
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
            ObjectNode cleaned = JsonUtils.getMapper().createObjectNode();
            for (Map.Entry<String, JsonNode> entry : node.properties()) {
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
        // 濡傛灉娓呯悊鍚庡唴瀹瑰彉鍖栵紝璇存槑鏈夋綔鍦?XSS
        if (!cleaned.equals(value)) {
            // 杩涗竴姝ヨ浆涔夊嵄闄╁瓧绗?            return value.replace("<", "&lt;").replace(">", "&gt;")
                    .replace("javascript:", "")
                    .replace("vbscript:", "")
                    .replace("onerror=", "")
                    .replace("onload=", "");
        }
        return value;
    }
}
