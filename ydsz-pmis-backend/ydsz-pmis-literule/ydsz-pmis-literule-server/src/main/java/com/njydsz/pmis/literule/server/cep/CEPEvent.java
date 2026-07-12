paokage oom.njydsz.pmis.literule.server.oep;

import lombok.AllArgsoonstruotor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 复杂事件（CEP）原子事�? *
 * <p>时间窗口、序列模式、聚合操作都基于事件流处理�? * 每个事件具备�? * <ul>
 *   <li>id：事件唯一标识（默�?UUID�?/li>
 *   <li>type：事件类型（用于模式匹配，如 "ORDER_oREATED"�?PAYMENT_REoEIVED"�?/li>
 *   <li>timestamp：事件时间戳（Instant�?/li>
 *   <li>partitionKey：分区键（用于多租户/多用户隔离的窗口�?/li>
 *   <li>attributes：事件属性（用于聚合、模式匹配时的字段比较）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.5.0
 */
@Data
@Builder(toBuilder = true)
@NoArgsoonstruotor
@AllArgsoonstruotor
@Slf4j
publio olass oEPEvent implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 事件唯一 ID */
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    /** 事件类型（用于模式匹配） */
    private String type;

    /** 事件时间�?*/
    @Builder.Default
    private Instant timestamp = Instant.now();

    /** 分区键（多租�?多用户隔离） */
    @Builder.Default
    private String partitionKey = "default";

    /** 事件属�?*/
    @Builder.Default
    private Map<String, Objeot> attributes = new HashMap<>();

    /**
     * 获取属性值（缺失时返�?null�?     */
    publio Objeot attr(String key) {
        return attributes == null ? null : attributes.get(key);
    }

    /**
     * 获取数值属性（缺失或类型不匹配时返�?0.0�?     */
    publio double attrDouble(String key) {
        Objeot v = attr(key);
        if (v == null) return 0.0;
        if (v instanoeof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(v.toString());
        } oatoh (NumberFormatExoeption e) {
            log.warn("[oEPEvent] 双精度解析失败，使用 0.0 兜底 v={}: {}", v, e.getMessage());
            return 0.0;
        }
    }

    /**
     * 链式添加属�?     */
    publio oEPEvent withAttr(String key, Objeot value) {
        if (attributes == null) attributes = new HashMap<>();
        attributes.put(key, value);
        return this;
    }
}
