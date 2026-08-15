package com.njydsz.literule.web.controller;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.literule.server.cep.CEPEngine;
import com.njydsz.literule.server.cep.CEPEvent;
import com.njydsz.literule.server.cep.CEPHit;
import com.njydsz.literule.server.cep.CEPPattern;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.literule.domain.enums.LiteruleExceptionCode;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;

/**
 * CEP 模式测试 Controller（P2-7）
 *
 * <p>提供 CEP 模式的即时测试能力：注册一个临时模式，按顺序投递测试事件，
 * 收集命中结果后立即注销该模式。用于可视化编辑器中的「测试」按钮。
 *
 * <p><b>拆分说明：</b>本类从原 {@link CEPController} 拆分而来，仅保留模式测试接口。
 * 模式管理 / 事件投递 / 命中查询 / 引擎状态见 {@link CEPController}。
 *
 * <h3>使用场景</h3>
 * <p>用户在可视化编辑器中配置好 CEP 模式后，投递模拟事件流，即时查看是否命中及命中详情，
 * 无需持久化模式定义。测试完成后临时模式自动注销，不影响生产环境。
 *
 * <h3>请求体示例</h3>
 * <pre>
 * POST /ruleEngine/cep/patterns/test
 * {
 *   "pattern": { "id": "TEST_TMP", "type": "TIME_WINDOW", ... },
 *   "events": [
 *     { "type": "LOGIN_FAILED", "partitionKey": "u1" },
 *     { "type": "LOGIN_FAILED", "partitionKey": "u1" }
 *   ]
 * }
 * </pre>
 *
 * <h3>执行流程</h3>
 * <ol>
 *   <li>解析请求体中的 pattern 和 events</li>
 *   <li>注册临时模式到 CEP 引擎</li>
 *   <li>注册临时监听器收集命中</li>
 *   <li>按顺序投递测试事件</li>
 *   <li>投递完成后注销监听器和临时模式（finally 块保证清理）</li>
 *   <li>返回命中列表、命中数、投递事件数</li>
 * </ol>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see CEPController CEP 核心接口（模式管理 / 事件投递 / 命中查询 / 状态）
 * @see CEPEngine CEP 引擎
 * @see CEPPattern CEP 模式定义
 */
@Slf4j
@RestController
@RequestMapping("/ruleEngine/cep")
@RequiredArgsConstructor
@Tag(name = "CEP 模式测试", description = "CEP 模式即时测试（注册临时模式 → 投递事件 → 收集命中 → 注销）")
public class CEPTestController {

    /** CEP 引擎（条件装配，未启用时为空） */
    private final ObjectProvider<CEPEngine> cepEngineProvider;

    /**
     * 测试 CEP 模式（P2-7）。
     *
     * <p>注册一个临时模式，按顺序投递测试事件，收集命中结果后立即注销该模式。
     * 用于可视化编辑器中的「测试」按钮：用户配置好模式后投递模拟事件流，
     * 即时查看是否命中及命中详情，无需持久化模式定义。
     *
     * @param body 包含 pattern 和 events 的请求体
     * @return 测试结果（含命中列表、命中数、投递事件数）
     */
    @Idempotent(key = "cep:testPattern", ttlSeconds = 5, message = "请勿重复提交")
    @Audit(module = "CEP管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'testPattern'")
    @RateLimit(resource = "literule.c_e_p_test.testPattern", threshold = 50)
    @PostMapping("/patterns/test")
    @Operation(summary = "测试 CEP 模式",
            description = "注册临时模式 → 投递测试事件 → 收集命中 → 自动注销临时模式")
    public BaseResponse<Map<String, Object>> testPattern(@RequestBody Map<String, Object> body) {
        CEPEngine engine = cepEngineProvider.getIfAvailable();
        if (engine == null) {
            return BaseResponse.error(BaseResultCode.FORBIDDEN, "CEP 引擎未启用");
        }
        try {
            Object patternObj = body.get("pattern");
            if (patternObj == null) {
                return BaseResponse.error(BaseResultCode.VALIDATION_FAILED, "pattern 不能为空");
            }
            CEPPattern pattern = YdszJson.convertValue(patternObj, CEPPattern.class);
            if (pattern.getId() == null || pattern.getId().isBlank()) {
                pattern.setId("TEST_TMP_" + System.nanoTime());
            }
            String patternId = pattern.getId();
            Object eventsObj = body.get("events");
            if (!(eventsObj instanceof List<?> eventsList)) {
                return BaseResponse.error(BaseResultCode.VALIDATION_FAILED, "events 必须为数组");
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
            return BaseResponse.success(result);
        } catch (Exception e) {
            log.warn("[CEP] 测试模式失败: {}", e.getMessage());
            return BaseResponse.error(LiteruleExceptionCode.RULE_EXPRESSION_INVALID, "测试失败: " + e.getMessage());
        }
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
