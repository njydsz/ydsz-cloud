package com.njydsz.pmis.literule.cep;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * CEP 模式工厂
 *
 * <p>用于根据 Map / JSON 构造 CEPPattern，避免在业务代码中重复 builder 调用。
 * 同时提供常见业务模式的快捷构造方法。
 */
@Slf4j
public class CEPPatternFactory implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 时间窗口计数模式（如"3 分钟内 5 次登录失败"）
     *
     * @param id         模式 ID
     * @param ruleCode   命中后触发的规则编码
     * @param eventType  事件类型
     * @param window     时间窗口
     * @param threshold  触发阈值（事件次数）
     */
    public static CEPPattern timeWindow(String id, String ruleCode, String eventType,
                                        Duration window, int threshold) {
        return CEPPattern.builder()
                .id(id)
                .type(CEPPattern.PatternType.TIME_WINDOW)
                .ruleCode(ruleCode)
                .eventType(eventType)
                .window(window)
                .threshold(threshold)
                .build();
    }

    /**
     * 序列模式（A → B → C 按顺序匹配）
     *
     * @param id        模式 ID
     * @param ruleCode  命中后触发的规则编码
     * @param window    时间窗口（从第一步到最后一步的总时长）
     * @param steps     序列步骤
     */
    public static CEPPattern sequence(String id, String ruleCode, Duration window,
                                      List<CEPPattern.SequenceStep> steps) {
        return CEPPattern.builder()
                .id(id)
                .type(CEPPattern.PatternType.SEQUENCE)
                .ruleCode(ruleCode)
                .window(window)
                .sequence(steps)
                .build();
    }

    /**
     * 聚合模式（窗口内 SUM/AVG/COUNT/MIN/MAX）
     */
    public static CEPPattern aggregate(String id, String ruleCode, String eventType, String field,
                                       CEPPattern.AggregateFunction func,
                                       Duration window, double threshold) {
        return CEPPattern.builder()
                .id(id)
                .type(CEPPattern.PatternType.AGGREGATE)
                .ruleCode(ruleCode)
                .eventType(eventType)
                .aggregateField(field)
                .aggregateFunction(func)
                .window(window)
                .threshold(threshold)
                .build();
    }

    /**
     * 缺失模式（窗口内期待某类型事件出现，否则触发告警）
     */
    public static CEPPattern absence(String id, String ruleCode, String expectedType,
                                     Duration window, double threshold) {
        return CEPPattern.builder()
                .id(id)
                .type(CEPPattern.PatternType.ABSENCE)
                .ruleCode(ruleCode)
                .eventType(expectedType)
                .window(window)
                .threshold(threshold)
                .build();
    }

    /**
     * 从 JSON 字符串反序列化
     */
    public static CEPPattern fromJson(String json) {
        try {
            return MAPPER.readValue(json, CEPPattern.class);
        } catch (Exception e) {
            log.warn("[CEP] JSON 反序列化失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 序列化为 JSON 字符串
     */
    public static String toJson(CEPPattern pattern) {
        try {
            return MAPPER.writeValueAsString(pattern);
        } catch (Exception e) {
            log.warn("[CEP] JSON 序列化失败: {}", e.getMessage());
            return "{}";
        }
    }
}
