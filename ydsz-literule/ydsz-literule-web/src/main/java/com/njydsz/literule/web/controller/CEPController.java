package com.njydsz.literule.web.controller;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.njydsz.literule.domain.vo.CEPHitVO;
import com.njydsz.literule.domain.vo.CEPPatternVO;
import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.literule.api.RuleContext;
import com.njydsz.literule.api.RuleEngine;
import com.njydsz.literule.api.RuleResult;
import com.njydsz.literule.server.cep.CEPEngine;
import com.njydsz.literule.server.cep.CEPEvent;
import com.njydsz.literule.server.cep.CEPHit;
import com.njydsz.literule.server.cep.CEPPattern;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;

/**
 * CEP 复杂事件处理 Controller（P0-2）— 模式管理 / 事件投递 / 命中查询 / 引擎状态
 *
 * <p>暴露 CEP 引擎的核心 REST API，支持：
 * <ul>
 *   <li><b>模式管理</b>：列出 / 注册 / 注销 CEP 模式</li>
 *   <li><b>事件投递</b>：单条 / 批量投递事件，触发模式匹配</li>
 *   <li><b>命中查询</b>：返回最近命中的模式记录（内存暂存，最多 200 条）</li>
 *   <li><b>引擎状态</b>：返回当前模式数、累计命中数</li>
 * </ul>
 *
 * <p>CEP 引擎通过 {@code ydsz.literule.cep.enabled} 控制装配，
 * 未启用时所有接口返回 503（通过 {@link ObjectProvider} 判空）。
 *
 * <p><b>拆分说明：</b>本类从原 {@code CEPController} 拆分而来，保留模式管理 / 事件投递 / 命中查询 / 引擎状态。
 * CEP 模式测试（注册临时模式 → 投递测试事件 → 收集命中 → 注销）见 {@link CEPTestController}。
 *
 * <h3>命中闭环</h3>
 * <p>启动时通过 {@link #init()} 注册 CEP 命中监听器：
 * <ol>
 *   <li>命中模式后存入 {@code recentHits} 供运维查询（最多 200 条）</li>
 *   <li>将命中事件作为事实投递给规则引擎，触发与 {@code pattern.ruleCode} 关联的规则评估</li>
 * </ol>
 * 形成 "CEP 命中 → 规则评估 → 预警" 的完整闭环。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see CEPTestController CEP 模式测试接口
 * @see CEPEngine CEP 引擎
 * @see CEPPattern CEP 模式定义
 */
@Slf4j
@RestController
@RequestMapping("/ruleEngine/cep")
@RequiredArgsConstructor
@Tag(name = "CEP 复杂事件处理", description = "模式管理 / 事件投递 / 命中查询 / 引擎状态")
public class CEPController {

    /** CEP 引擎（条件装配，未启用时为空） */
    private final ObjectProvider<CEPEngine> cepEngineProvider;
    /** 规则引擎（条件装配，未启用时为空） */
    private final ObjectProvider<RuleEngine> ruleEngineProvider;

    /** 最近命中记录（内存暂存，最多 200 条，用于运维查看） */
    private static final int MAX_RECENT_HITS = 200;
    private final List<CEPHit> recentHits = new ArrayList<>();

    /**
     * 启动时注册 CEP 命中监听器。
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
                    if (!Response.isEmpty()) {
                        log.info("[CEPController] CEP 命中触发规则评估: patternId={}, ruleCode={}, triggered={}",
                                hit.getPatternId(), hit.getRuleCode(), Response.size());
                    }
                } catch (Exception e) {
                    log.warn("[CEPController] CEP 命中触发规则评估异常: {}", e.getMessage());
                }
            }
        });
        log.info("[CEPController] CEP 命中监听器已注册");
    }

    /**
     * 列出已注册的 CEP 模式。
     *
     * @return 模式列表
     */
    @GetMapping("/patterns")
    @Operation(summary = "列出已注册的 CEP 模式")
    public BaseResponse<List<CEPPatternVO>> listPatterns() {
        CEPEngine engine = cepEngineProvider.getIfAvailable();
        if (engine == null) {
            return BaseResponse.error(BaseResultCode.FORBIDDEN, "CEP 引擎未启用");
        }
        return BaseResponse.success(engine.listPatterns().stream().map(this::toPatternVO).toList());
    }

    /**
     * 注册 CEP 模式。
     *
     * @param pattern 模式定义
     * @return 注册结果
     */
    @Idempotent(key = "cep:registerPattern", ttlSeconds = 5, message = "请勿重复提交")
    @Audit(module = "CEP管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'registerPattern'")
    @RateLimit(resource = "literule.c_e_p.registerPattern", threshold = 50)
    @PostMapping("/patterns")
    @Operation(summary = "注册 CEP 模式")
    public BaseResponse<Void> registerPattern(@RequestBody CEPPattern pattern) {
        CEPEngine engine = cepEngineProvider.getIfAvailable();
        if (engine == null) {
            return BaseResponse.error(BaseResultCode.FORBIDDEN, "CEP 引擎未启用");
        }
        try {
            engine.registerPattern(pattern);
            return BaseResponse.success();
        } catch (IllegalArgumentException e) {
            return BaseResponse.error(e.getMessage());
        }
    }

    /**
     * 注销 CEP 模式。
     *
     * @param patternId 模式 ID
     * @return 注销结果
     */
    @Idempotent(key = "cep:unregisterPattern", ttlSeconds = 5, message = "请勿重复提交")
    @Audit(module = "CEP管理", type = AuditType.OPERATION, action = AuditAction.DELETE, content = "'unregisterPattern'")
    @RateLimit(resource = "literule.c_e_p.unregisterPattern", threshold = 50)
    @DeleteMapping("/patterns/{patternId}")
    @Operation(summary = "注销 CEP 模式")
    public BaseResponse<Void> unregisterPattern(@PathVariable String patternId) {
        CEPEngine engine = cepEngineProvider.getIfAvailable();
        if (engine == null) {
            return BaseResponse.error(BaseResultCode.FORBIDDEN, "CEP 引擎未启用");
        }
        engine.unregisterPattern(patternId);
        return BaseResponse.success();
    }

    /**
     * 投递单条事件。
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
    @Audit(module = "CEP管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'feedEvent'")
    @RateLimit(resource = "literule.c_e_p.feedEvent", threshold = 50)
    @PostMapping("/events")
    @Operation(summary = "投递单条事件", description = "投递单条事件到 CEP 引擎，返回触发的命中数")
    public BaseResponse<Map<String, Object>> feedEvent(@RequestBody Map<String, Object> body) {
        CEPEngine engine = cepEngineProvider.getIfAvailable();
        if (engine == null) {
            return BaseResponse.error(BaseResultCode.FORBIDDEN, "CEP 引擎未启用");
        }
        int hitsBefore = (int) engine.totalHits();
        CEPEvent event = toEvent(body);
        engine.feed(event);
        int hitsAfter = (int) engine.totalHits();
        Map<String, Object> result = new HashMap<>();
        result.put("fed", true);
        result.put("triggeredHits", hitsAfter - hitsBefore);
        return BaseResponse.success(result);
    }

    /**
     * 批量投递事件。
     *
     * @param events 事件列表
     * @return 投递结果（含触发的命中数）
     */
    @Idempotent(key = "cep:feedEvents", ttlSeconds = 5, message = "请勿重复提交")
    @Audit(module = "CEP管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'feedEvents'")
    @RateLimit(resource = "literule.c_e_p.feedEvents", threshold = 50)
    @PostMapping("/events/batch")
    @Operation(summary = "批量投递事件", description = "批量投递事件到 CEP 引擎，返回触发的命中数")
    public BaseResponse<Map<String, Object>> feedEvents(@RequestBody List<Map<String, Object>> events) {
        CEPEngine engine = cepEngineProvider.getIfAvailable();
        if (engine == null) {
            return BaseResponse.error(BaseResultCode.FORBIDDEN, "CEP 引擎未启用");
        }
        if (events == null || events.isEmpty()) {
            return BaseResponse.success(Map.of("fed", 0, "triggeredHits", 0));
        }
        int hitsBefore = (int) engine.totalHits();
        for (Map<String, Object> body : events) {
            engine.feed(toEvent(body));
        }
        int hitsAfter = (int) engine.totalHits();
        return BaseResponse.success(Map.of("fed", events.size(), "triggeredHits", hitsAfter - hitsBefore));
    }

    /**
     * 查询最近命中记录。
     *
     * @return 命中记录列表（最多 200 条）
     */
    @GetMapping("/hits")
    @Operation(summary = "查询最近命中记录", description = "返回最近 200 条 CEP 命中记录（内存暂存）")
    public BaseResponse<List<CEPHitVO>> recentHits() {
        synchronized (recentHits) {
            return BaseResponse.success(new ArrayList<>(recentHits).stream().map(this::toHitVO).toList());
        }
    }

    /**
     * CEP 引擎状态。
     *
     * @return 状态信息（模式数、命中数）
     */
    @GetMapping("/stats")
    @Operation(summary = "CEP 引擎状态", description = "返回当前模式数和累计命中数")
    public BaseResponse<Map<String, Object>> stats() {
        CEPEngine engine = cepEngineProvider.getIfAvailable();
        if (engine == null) {
            return BaseResponse.error(BaseResultCode.FORBIDDEN, "CEP 引擎未启用");
        }
        return BaseResponse.success(Map.of(
                "patternCount", engine.patternCount(),
                "totalHits", engine.totalHits()
        ));
    }

    /**
     * CEPPattern → CEPPatternVO 转换
     */
    private CEPPatternVO toPatternVO(CEPPattern p) {
        CEPPatternVO vo = new CEPPatternVO();
        vo.setId(p.getId());
        vo.setType(p.getType() != null ? p.getType().name() : null);
        vo.setRuleCode(p.getRuleCode());
        vo.setName(p.getName());
        vo.setWindow(p.getWindow());
        vo.setSlide(p.getSlide());
        vo.setWindowType(p.getWindowType() != null ? p.getWindowType().name() : null);
        vo.setSessionGap(p.getSessionGap());
        vo.setThreshold(p.getThreshold());
        vo.setEventType(p.getEventType());
        vo.setFilter(p.getFilter());
        vo.setAggregateFunction(p.getAggregateFunction() != null ? p.getAggregateFunction().name() : null);
        vo.setAggregateField(p.getAggregateField());
        vo.setSequence(p.getSequence() == null ? null : p.getSequence().stream().map(e -> (Object) e).toList());
        vo.setDescription(p.getDescription());
        return vo;
    }

    /**
     * CEPHit → CEPHitVO 转换
     */
    private CEPHitVO toHitVO(CEPHit h) {
        CEPHitVO vo = new CEPHitVO();
        vo.setPatternId(h.getPatternId());
        vo.setRuleCode(h.getRuleCode());
        vo.setMatchedEvents(h.getMatchedEvents() == null ? null : h.getMatchedEvents().stream().map(e -> (Object) e).toList());
        vo.setHitAt(h.getHitAt());
        vo.setMetric(h.getMetric());
        vo.setContext(h.getContext());
        return vo;
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
}
