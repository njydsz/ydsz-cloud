package com.njydsz.pmis.agent.web.controller.agent;

import com.njydsz.pmis.agent.server.engine.react.ReActStep;
import com.njydsz.pmis.agent.server.engine.trace.TraceEvent;
import com.njydsz.pmis.agent.server.engine.trace.TraceRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Agent Trace 可视化与回放 API（P2-3 落地）。
 *
 * <p>对标 LangSmith Trace / Langfuse / Coze 调试追踪 / Dify Trace：
 * 提供完整的 Agent 执行链路追踪可视化，支持：
 * <ul>
 *   <li><b>Trace 查询</b> - 按 traceId / sessionId / 时间范围查询执行链路</li>
 *   <li><b>Trace 回放</b> - 逐步重放 ReAct 循环的每个步骤</li>
 *   <li><b>Trace 时间线</b> - 可视化展示各步骤的时间分布</li>
 *   <li><b>Trace 对比</b> - 对比两次执行的差异</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.5.0 (P2-3)
 */
@Slf4j
@RestController
@RequestMapping("/agent/trace")
@RequiredArgsConstructor
public class AgentTraceController {

    private final TraceRecorder traceRecorder;

    /**
     * 按 traceId 查询完整执行链路。
     *
     * @param traceId 追踪 ID
     * @return 完整链路信息
     */
    @GetMapping("/{traceId}")
    public ResponseEntity<Map<String, Object>> getTrace(@PathVariable String traceId) {
        Map<String, Object> response = new LinkedHashMap<>();

        List<TraceEvent> events = traceRecorder.getEvents(traceId);
        if (events == null || events.isEmpty()) {
            response.put("found", false);
            response.put("message", "Trace 不存在或已过期");
            return ResponseEntity.ok(response);
        }

        response.put("found", true);
        response.put("traceId", traceId);
        response.put("totalEvents", events.size());

        // 构建时间线
        List<Map<String, Object>> timeline = new ArrayList<>();
        long startTime = events.get(0).getTimestamp();
        long endTime = events.get(events.size() - 1).getTimestamp();
        response.put("totalDurationMs", endTime - startTime);

        for (TraceEvent event : events) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("eventIndex", timeline.size());
            item.put("type", event.getType());
            item.put("nodeName", event.getNodeName());
            item.put("message", event.getMessage());
            item.put("timestamp", event.getTimestamp());
            item.put("relativeMs", event.getTimestamp() - startTime);
            item.put("data", event.getData());
            timeline.add(item);
        }
        response.put("timeline", timeline);

        // 构建步骤摘要
        List<Map<String, Object>> steps = new ArrayList<>();
        int currentStep = 0;
        for (TraceEvent event : events) {
            if ("STEP_START".equals(event.getType()) || "STARTED".equals(event.getType())) {
                currentStep++;
                Map<String, Object> step = new LinkedHashMap<>();
                step.put("step", currentStep);
                step.put("startMs", event.getTimestamp() - startTime);
                steps.add(step);
            } else if ("SUCCESS".equals(event.getType()) || "STEP_END".equals(event.getType())) {
                if (!steps.isEmpty()) {
                    steps.get(steps.size() - 1).put("endMs", event.getTimestamp() - startTime);
                    steps.get(steps.size() - 1).put("durationMs",
                            event.getTimestamp() - startTime
                                    - (long) steps.get(steps.size() - 1).get("startMs"));
                }
            }
        }
        response.put("steps", steps);

        return ResponseEntity.ok(response);
    }

    /**
     * 获取 Trace 列表（按时间范围）。
     *
     * @param sessionId 会话 ID（可选）
     * @param limit     返回数量
     * @return Trace 列表
     */
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> listTraces(
            @RequestParam(required = false) String sessionId,
            @RequestParam(defaultValue = "20") int limit) {
        Map<String, Object> response = new LinkedHashMap<>();

        List<String> traceIds = traceRecorder.listTraceIds(sessionId, limit);
        List<Map<String, Object>> traces = new ArrayList<>();
        for (String traceId : traceIds) {
            Map<String, Object> trace = new LinkedHashMap<>();
            trace.put("traceId", traceId);
            List<TraceEvent> events = traceRecorder.getEvents(traceId);
            if (events != null && !events.isEmpty()) {
                trace.put("eventCount", events.size());
                trace.put("startTime", events.get(0).getTimestamp());
                trace.put("endTime", events.get(events.size() - 1).getTimestamp());
                trace.put("durationMs",
                        events.get(events.size() - 1).getTimestamp() - events.get(0).getTimestamp());
            }
            traces.add(trace);
        }
        response.put("traces", traces);
        response.put("total", traces.size());
        return ResponseEntity.ok(response);
    }

    /**
     * 回放某个 Trace（逐步返回事件）。
     *
     * @param traceId 追踪 ID
     * @param step    从第几步开始回放（1-based）
     * @return 回放数据
     */
    @GetMapping("/{traceId}/replay")
    public ResponseEntity<Map<String, Object>> replayTrace(
            @PathVariable String traceId,
            @RequestParam(defaultValue = "1") int step) {
        Map<String, Object> response = new LinkedHashMap<>();

        List<TraceEvent> events = traceRecorder.getEvents(traceId);
        if (events == null || events.isEmpty()) {
            response.put("found", false);
            return ResponseEntity.ok(response);
        }

        response.put("found", true);
        response.put("traceId", traceId);
        response.put("totalSteps", events.size());
        response.put("currentStep", step);

        if (step > 0 && step <= events.size()) {
            TraceEvent event = events.get(step - 1);
            response.put("event", Map.of(
                    "type", event.getType(),
                    "nodeName", event.getNodeName() != null ? event.getNodeName() : "",
                    "message", event.getMessage() != null ? event.getMessage() : "",
                    "data", event.getData() != null ? event.getData() : Map.of()
            ));
            response.put("hasNext", step < events.size());
            response.put("hasPrev", step > 1);
        }

        return ResponseEntity.ok(response);
    }
}
