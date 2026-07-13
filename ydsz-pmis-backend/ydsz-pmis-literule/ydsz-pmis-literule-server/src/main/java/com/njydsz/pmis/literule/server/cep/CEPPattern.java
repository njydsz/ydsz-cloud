package com.njydsz.pmis.literule.server.cep;

import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * CEP 模式定义（P2-13）
 *
 * <p>模式由若干步骤组成，每个步骤描述如何匹配一个事件。支持的模式类型：
 * <ul>
 *   <li>TIME_WINDOW：时间窗口（滚动/滑动）。当窗口内匹配的事件数达到阈值时触发</li>
 *   <li>SEQUENCE：序列模式。按步骤顺序匹配事件 A → B → C，全部在窗口内匹配则触发</li>
 *   <li>AGGREGATE：聚合模式。窗口内对数值属性做 SUM/AVG/COUNT/MIN/MAX，达到阈值时触发</li>
 *   <li>ABSENCE：缺失模式。期望某类型事件在窗口内出现，否则触发（用于告警）</li>
 * </ul>
 *
 * <p>例如：
 * <pre>
 * Pattern: 检测 "3 分钟内 5 次登录失败"
 * - type: TIME_WINDOW
 * - eventType: LOGIN_FAILED
 * - window: 3 分钟
 * - threshold: 5
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class CEPPattern implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 模式类型 */
    public enum PatternType {
        /** 时间窗口计数（窗口内 N 次事件） */
        TIME_WINDOW,
        /** 序列模式（按步骤依次匹配） */
        SEQUENCE,
        /** 聚合模式（窗口内 SUM/AVG/COUNT/MIN/MAX） */
        AGGREGATE,
        /** 缺失模式（窗口内某类事件应出现但未出现） */
        ABSENCE
    }

    /** 聚合函数 */
    public enum AggregateFunction {
        COUNT, SUM, AVG, MIN, MAX
    }

    /** 窗口类型（2.0.0 CEP 窗口语义增强） */
    public enum WindowType {
        /** 滚动窗口：固定大小、不重叠、每次到期后清空 */
        TUMBLING,
        /** 滑动窗口：固定大小、按滑动步长推进、窗口可重叠 */
        SLIDING,
        /** 会话窗口：由事件间隔驱动，超过 gap 则关闭当前窗口 */
        SESSION,
        /** 计数窗口：按事件数量计数，达到阈值后触发并清空 */
        COUNT
    }

    /** 模式唯一标识 */
    private String id;

    /** 模式类型 */
    private PatternType type;

    /** 关联的规则编码（命中模式时触发的规则） */
    private String ruleCode;

    /** 模式名称（中文） */
    private String name;

    /** 时间窗口长度 */
    private Duration window;

    /** 滑动步长（仅 SLIDING 类型；null 表示滚动窗口） */
    private Duration slide;

    /** 窗口类型（2.0.0，默认 TUMBLING 兼容旧版） */
    private WindowType windowType;

    /** 会话窗口空闲超时（仅 SESSION 类型，超过此间隔关闭当前窗口） */
    private Duration sessionGap;

    /** 计数窗口阈值（仅 COUNT 类型，事件数达到此值时触发并清空） */
    @Builder.Default
    private int countWindow = 0;

    /** 触发阈值（TIME_WINDOW 模式下为次数，AGGREGATE 模式下为数值阈值） */
    private double threshold;

    /** 事件类型（单事件类型匹配） */
    private String eventType;

    /** 事件类型列表（多类型 OR 匹配，如 LOGIN_FAILED 或 LOGIN_TIMEOUT） */
    private List<String> eventTypes;

    /** 事件过滤条件（LiteExpr 表达式，可访问 $event.attr('xxx')） */
    private String filter;

    /** 聚合函数（AGGREGATE 模式使用） */
    private AggregateFunction aggregateFunction;

    /** 聚合字段（AGGREGATE 模式使用） */
    private String aggregateField;

    /** 序列步骤（SEQUENCE 模式使用），按顺序匹配 */
    private List<SequenceStep> sequence;

    /** 描述 */
    private String description;

    /**
     * 序列步骤
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SequenceStep implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /** 步骤序号（从 1 开始） */
        private int order;

        /** 该步骤匹配的事件类型 */
        private String eventType;

        /** 该步骤的事件过滤条件 */
        private String filter;

        /** 该步骤与下一步的最小间隔（null 表示无限制） */
        private Duration minGap;

        /** 该步骤与下一步的最大间隔（null 表示无限制） */
        private Duration maxGap;
    }

    /**
     * 从 Map 反序列化（用于 SQL JSON 字段）
     */
    @SuppressWarnings("unchecked")
    public static CEPPattern fromMap(Map<String, Object> map) {
        if (map == null) return null;
        return CEPPattern.builder()
                .id((String) map.get("id"))
                .type(map.get("type") != null ? PatternType.valueOf((String) map.get("type")) : null)
                .ruleCode((String) map.get("ruleCode"))
                .name((String) map.get("name"))
                .description((String) map.get("description"))
                .eventType((String) map.get("eventType"))
                .eventTypes((List<String>) map.get("eventTypes"))
                .filter((String) map.get("filter"))
                .aggregateFunction(map.get("aggregateFunction") != null
                        ? AggregateFunction.valueOf((String) map.get("aggregateFunction")) : null)
                .aggregateField((String) map.get("aggregateField"))
                .threshold(toDouble(map.get("threshold")))
                .build();
    }

    private static double toDouble(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(v.toString());
        } catch (NumberFormatException e) {
            log.warn("[CEPPattern] 双精度解析失败，使用 0 兜底 v={}: {}", v, e.getMessage());
            return 0;
        }
    }
}
