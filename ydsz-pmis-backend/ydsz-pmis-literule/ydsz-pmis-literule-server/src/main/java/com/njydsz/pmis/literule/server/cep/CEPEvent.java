package com.njydsz.pmis.literule.server.cep;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 复杂事件（CEP）原子事件
 *
 * <p>时间窗口、序列模式、聚合操作都基于事件流处理。
 * 每个事件具备：
 * <ul>
 *   <li>id：事件唯一标识（默认 UUID）</li>
 *   <li>type：事件类型（用于模式匹配，如 "ORDER_CREATED"、"PAYMENT_RECEIVED"）</li>
 *   <li>timestamp：事件时间戳（Instant）</li>
 *   <li>partitionKey：分区键（用于多租户/多用户隔离的窗口）</li>
 *   <li>attributes：事件属性（用于聚合、模式匹配时的字段比较）</li>
 * </ul>
 *
 * @since 1.5.0
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class CEPEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 事件唯一 ID */
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    /** 事件类型（用于模式匹配） */
    private String type;

    /** 事件时间戳 */
    @Builder.Default
    private Instant timestamp = Instant.now();

    /** 分区键（多租户/多用户隔离） */
    @Builder.Default
    private String partitionKey = "default";

    /** 事件属性 */
    @Builder.Default
    private Map<String, Object> attributes = new HashMap<>();

    /**
     * 获取属性值（缺失时返回 null）
     */
    public Object attr(String key) {
        return attributes == null ? null : attributes.get(key);
    }

    /**
     * 获取数值属性（缺失或类型不匹配时返回 0.0）
     */
    public double attrDouble(String key) {
        Object v = attr(key);
        if (v == null) return 0.0;
        if (v instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(v.toString());
        } catch (NumberFormatException e) {
            log.warn("[CEPEvent] 双精度解析失败，使用 0.0 兜底 v={}: {}", v, e.getMessage());
            return 0.0;
        }
    }

    /**
     * 链式添加属性
     */
    public CEPEvent withAttr(String key, Object value) {
        if (attributes == null) attributes = new HashMap<>();
        attributes.put(key, value);
        return this;
    }
}
