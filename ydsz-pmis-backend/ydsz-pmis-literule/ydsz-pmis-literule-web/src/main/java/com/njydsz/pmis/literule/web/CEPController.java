package com.njydsz.pmis.literule.web;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.lock.annotation.Idempotent;
import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleEngine;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.server.cep.CEPEngine;
import com.njydsz.pmis.literule.server.cep.CEPEvent;
import com.njydsz.pmis.literule.server.cep.CEPHit;
import com.njydsz.pmis.literule.server.cep.CEPPattern;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * CEP 复杂事件处理 Controller（P0-2）
 *
 * <p>暴露 CEP 引擎的 REST API，支持：
 * <ul>
 *   <li>模式管理：注册 / 注销 / 列出模式</li>
 *   <li>事件投递：单条 / 批量投递事件，触发模式匹配</li>
 *   <li>命中查询：返回最近命中的模式记录（内存暂存，最多 200 条）</li>
 * </ul>
 *
 * <p>CEP 引擎通过 {@code pmis.literule.cep.enabled} 控制装配，
 * 未启用时所有接口返回 503（通过 ObjectProvider 判空）。
 *
 * @author ydsz-pmis-team
 * @since 1.5.1
 */
@Slf4j
@RestController
@RequestMapping("/ruleEngine/cep")
@RequiredArgsConstructor
@Tag(name = "CEP 复杂事件处理", description = "时间窗口/序列/聚合/缺失模式匹配")
public class CEPController {

    /** CEP 引擎（条件装配，未启用时为空） */
    private final ObjectProvider<CEPEngine> cepEngineProvider;
    /** 规则引擎（条件装配，未启用时为空） */
    private final ObjectProvider<RuleEngine> ruleEngineProvider;

    /** 最近命中记录（内存暂存，最多 200 条，用于运维查看） */
    private static final int MAX_RECENT_HITS = 200;
    private final List<CEPHit> recentHits = new ArrayList<>();

    /** ObjectMapper 用于 CEP 模式反序列化（P2-7 测试模式端点使用） */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 启动时注册 CEP 命中监听器
     *
     * <p>命中模式后：① 存入 recentHits 供运维查询；② 将命中事件作为事实
     * 投递给规则引擎，触发与 pattern.ruleCode 关联的规则评估，形成
     * "CEP 命中 → 规则评估 → 预警"的完整闭环。
     */
    @PostConstruct
    public void init() {
        CEPEngine engine = cepEngineProvider.getIfAvailable();
        if (engine == null) {
            log.info("[CEPController] CEP 引擎未启用，跳过监听器注册");
            return;
        }
        engine.addListener(hit -> {
            // 1. 存入最近命中
            synchronized (recentHits) {
                recentHits.add(hit);
                while (recentHits.size() > MAX_RECENT_HITS) {
                    recentHits.remove(0);
                }
            }
            // 2. 触发关联规则评估
            RuleEngine ruleEngine = ruleEngineProvider.getIfAvailable();
            if (ruleEngine != null && hit.getRuleCode() != null) {
                try {
                    Map<String, Object> facts = new HashMap<>();
                    facts.put("cepHit", hit);
                    facts.put("patternId", hit.getPatternId());
                    facts.put("ruleCode", hit.getRuleCode());
                    facts.put("metric", hit.getMetric());
                    facts.put("matchedCount", hit.getMatchedEvents() != null ? hit.getMatchedEvents().size() : 0);
                    if (hit.getContext() != null) {
                        facts.putAll(hit.getContext());
                    }
                    RuleContext ctx = RuleContext.of(facts, "CEP", "CEP_ENGINE", null);
                    List<RuleResult> results = ruleEngine.evaluate(ctx);
                    if (!results.isEmpty()) {
                        log.info("[CEPController] CEP 命中触发规则评估: patternId={}, ruleCode={}, triggered={}",
                                hit.getPatternId(), hit.getRuleCode(), results.size());
                    }
                } catch (Exception e) {
                    log.warn("[CEPController] CEP 命中触发规则评估异常: {}", e.getMessage());
                }
            }
        });
        log.info("[CEPController] CEP 命中监听器已注册");
    }

    /**
     * 列出已注册的 CEP 模式
     *
     * @return 模式列表
     */
    @GetMapping("/patterns")
    public BaseResponse<List<CEPPattern>> listPatterns() {
        CEPEngine engine = cepEngineProvider.getIfAvailable();
        if (engine == null) {
            return BaseResponse.fail("CEP 引擎未启用");
        }
        return BaseResponse.ok(engine.listPatterns());
    }

    /**
     * 注册 CEP 模式
     *
     * @param pattern 模式定义
     * @return 注册结果
     */
    @Idempotent(key = "cep:registerPattern", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/patterns")
    public BaseResponse<Void> registerPattern(@RequestBody CEPPattern pattern) {
        CEPEngine engine = cepEngineProvider.getIfAvailable();
        if (engine == null) {
            return BaseResponse.fail("CEP 引擎未启用");
        }
        try {
            engine.registerPattern(pattern);
            return BaseResponse.ok();
        } catch (IllegalArgumentException e) {
            return BaseResponse.fail(e.getMessage());
        }
    }

    /**
     * 注销 CEP 模式
     *
     * @param patternId 模式 ID
     * @return 注销结果
     */
    @Idempotent(key = "cep:unregisterPattern", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/patterns/{patternId}")
    public BaseResponse<Void> unregisterPattern(@PathVariable String patternId) {
        CEPEngine engine = cepEngineProvider.getIfAvailable();
        if (engine == null) {
            return BaseResponse.fail("CEP 引擎未启用");
        }
        engine.unregisterPattern(patternId);
        return BaseResponse.ok();
    }

    /**
     * 投递单条事件
     *
     * <p>请求体格式：
     * <pre>
     * {
     *   "type": "LOGIN_FAILED",
     *   "partitionKey": "user-001",
     *   "timestamp": "2026-07-07T12:00:00Z",  // 可选，默认当前时间
     *   "attributes": { "ip": "1.2.3.4" }
     * }
     * </pre>
     *
     * @param body 事件内容
     * @return 投递结果（含本次事件触发的命中数）
     */
    @Idempotent(key = "cep:feedEvent", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/events")
    public BaseResponse<Map<String, Object>> feedEvent(@RequestBody Map<String, Object> body) {
        CEPEngine engine = cepEngineProvider.getIfAvailable();
        if (engine == null) {
            return BaseResponse.fail("CEP 引擎未启用");
        }
        int hitsBefore = (int) engine.totalHits();
        CEPEvent event = toEvent(body);
        engine.feed(event);
        int hitsAfter = (int) engine.totalHits();
        Map<String, Object> result = new HashMap<>();
        result.put("fed", true);
        result.put("triggeredHits", hitsAfter - hitsBefore);
        return BaseResponse.ok(result);
    }

    /**
     * 批量投递事件
     *
     * @param events 事件列表
     * @return 投递结果（含触发的命中数）
     */
    @Idempotent(key = "cep:feedEvents", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/events/batch")
    public BaseResponse<Map<String, Object>> feedEvents(@RequestBody List<Map<String, Object>> events) {
        CEPEngine engine = cepEngineProvider.getIfAvailable();
        if (engine == null) {
            return BaseResponse.fail("CEP 引擎未启用");
        }
        if (events == null || events.isEmpty()) {
            return BaseResponse.ok(Map.of("fed", 0, "triggeredHits", 0));
        }
        int hitsBefore = (int) engine.totalHits();
        for (Map<String, Object> body : events) {
            engine.feed(toEvent(body));
        }
        int hitsAfter = (int) engine.totalHits();
        return BaseResponse.ok(Map.of("fed", events.size(), "triggeredHits", hitsAfter - hitsBefore));
    }

    /**
     * 查询最近命中记录
     *
     * @return 命中记录列表（最多 200 条）
     */
    @GetMapping("/hits")
    public BaseResponse<List<CEPHit>> recentHits() {
        synchronized (recentHits) {
            return BaseResponse.ok(new ArrayList<>(recentHits));
        }
    }

    /**
     * CEP 引擎状态
     *
     * @return 状态信息（模式数、命中数）
     */
    @GetMapping("/stats")
    public BaseResponse<Map<String, Object>> stats() {
        CEPEngine engine = cepEngineProvider.getIfAvailable();
        if (engine == null) {
            return BaseResponse.fail("CEP 引擎未启用");
        }
        return BaseResponse.ok(Map.of(
                "patternCount", engine.patternCount(),
                "totalHits", engine.totalHits()
        ));
    }

    /**
     * Map → CEPEvent 转换
     */
    private CEPEvent toEvent(Map<String, Object> body) {
        CEPEvent.CEPEventBuilder b = CEPEvent.builder();
        if (body.get("type") != null) {
            b.type(String.valueOf(body.get("type")));
        }
        if (body.get("partitionKey") != null) {
            b.partitionKey(String.valueOf(body.get("partitionKey")));
        }
        if (body.get("timestamp") != null) {
            try {
                b.timestamp(Instant.parse(String.valueOf(body.get("timestamp"))));
            } catch (Exception e) {
                log.debug("[CEP] timestamp 解析失败，使用当前时间: {}", body.get("timestamp"));
            }
        }
        Object attrs = body.get("attributes");
        if (attrs instanceof Map<?, ?> rawMap) {
            Map<String, Object> typed = new HashMap<>();
            rawMap.forEach((k, v) -> typed.put(String.valueOf(k), v));
            b.attributes(typed);
        }
        return b.build();
    }

    /**
     * 测试 CEP 模式（P2-7）
     *
     * <p>注册一个临时模式，按顺序投递测试事件，收集命中结果后立即注销该模式。
     * 用于可视化编辑器中的"测试"按钮：用户配置好模式后投递模拟事件流，
     * 即时查看是否命中及命中详情，无需持久化模式定义。
     *
     * <p>请求体示例：
     * <pre>
     * POST /execution/rules/cep/patterns/test
     * {
     *   "pattern": { "id": "TEST_TMP", "type": "TIME_WINDOW", ... },
     *   "events": [
     *     { "type": "LOGIN_FAILED", "partitionKey": "u1" },
     *     { "type": "LOGIN_FAILED", "partitionKey": "u1" }
     *   ]
     * }
     * </pre>
     *
     * @param body 包含 pattern 和 events 的请求体
     * @return 测试结果（含命中列表、命中数、投递事件数）
     */
    @Idempotent(key = "cep:testPattern", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/patterns/test")
    public BaseResponse<Map<String, Object>> testPattern(@RequestBody Map<String, Object> body) {
        CEPEngine engine = cepEngineProvider.getIfAvailable();
        if (engine == null) {
            return BaseResponse.fail("CEP 引擎未启用");
        }
        try {
            Object patternObj = body.get("pattern");
            if (patternObj == null) {
                return BaseResponse.fail("pattern 不能为空");
            }
            CEPPattern pattern = objectMapper.convertValue(patternObj, CEPPattern.class);
            if (pattern.getId() == null || pattern.getId().isBlank()) {
                pattern.setId("TEST_TMP_" + System.nanoTime());
            }
            String patternId = pattern.getId();
            Object eventsObj = body.get("events");
            if (!(eventsObj instanceof List<?> eventsList)) {
                return BaseResponse.fail("events 必须为数组");
            }

            // 注册临时模式
            engine.registerPattern(pattern);
            // 注册监听器收集命中
            List<CEPHit> testHits = new ArrayList<>();
            Consumer<CEPHit> listener = testHits::add;
            engine.addListener(listener);
            long hitsBefore = engine.totalHits();
            try {
                // 投递测试事件
                for (Object item : eventsList) {
                    if (item instanceof Map<?, ?> mp) {
                        Map<String, Object> eventBody = new HashMap<>();
                        mp.forEach((k, v) -> eventBody.put(String.valueOf(k), v));
                        engine.feed(toEvent(eventBody));
                    }
                }
            } finally {
                engine.removeListener(listener);
                engine.unregisterPattern(patternId);
            }
            long hitsAfter = engine.totalHits();

            Map<String, Object> result = new HashMap<>();
            result.put("patternId", patternId);
            result.put("fedEvents", eventsList.size());
            result.put("triggeredHits", hitsAfter - hitsBefore);
            result.put("hits", testHits);
            return BaseResponse.ok(result);
        } catch (Exception e) {
            log.warn("[CEP] 测试模式失败: {}", e.getMessage());
            return BaseResponse.fail("测试失败: " + e.getMessage());
        }
    }
}
