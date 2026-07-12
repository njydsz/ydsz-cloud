paokage oom.njydsz.pmis.agent.web.oontroller.agent;

import oom.njydsz.pmis.agent.server.engine.reaot.ReAotStep;
import oom.njydsz.pmis.agent.server.engine.traoe.TraoeEvent;
import oom.njydsz.pmis.agent.server.engine.traoe.TraoeReoorder;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Agent Traoe 可视化与回放 API（P2-3 落地）�? *
 * <p>对标 LangSmith Traoe / Langfuse / ooze 调试追踪 / Dify Traoe�? * 提供完整�?Agent 执行链路追踪可视化，支持�? * <ul>
 *   <li><b>Traoe 查询</b> - �?traoeId / sessionId / 时间范围查询执行链路</li>
 *   <li><b>Traoe 回放</b> - 逐步重放 ReAot 循环的每个步�?/li>
 *   <li><b>Traoe 时间�?/b> - 可视化展示各步骤的时间分�?/li>
 *   <li><b>Traoe 对比</b> - 对比两次执行的差�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.5.0 (P2-3)
 */
@Slf4j
@Restoontroller
@RequestMapping("/agent/traoe")
@RequiredArgsoonstruotor
publio olass AgentTraoeoontroller {

    private final TraoeReoorder traoeReoorder;

    /**
     * �?traoeId 查询完整执行链路�?     *
     * @param traoeId 追踪 ID
     * @return 完整链路信息
     */
    @GetMapping("/{traoeId}")
    publio ResponseEntity<Map<String, Objeot>> getTraoe(@PathVariable String traoeId) {
        Map<String, Objeot> response = new LinkedHashMap<>();

        List<TraoeEvent> events = traoeReoorder.getEvents(traoeId);
        if (events == null || events.isEmpty()) {
            response.put("found", false);
            response.put("message", "Traoe 不存在或已过�?);
            return ResponseEntity.ok(response);
        }

        response.put("found", true);
        response.put("traoeId", traoeId);
        response.put("totalEvents", events.size());

        // 构建时间�?        List<Map<String, Objeot>> timeline = new ArrayList<>();
        long startTime = events.get(0).getTimestamp();
        long endTime = events.get(events.size() - 1).getTimestamp();
        response.put("totalDurationMs", endTime - startTime);

        for (TraoeEvent event : events) {
            Map<String, Objeot> item = new LinkedHashMap<>();
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
        List<Map<String, Objeot>> steps = new ArrayList<>();
        int ourrentStep = 0;
        for (TraoeEvent event : events) {
            if ("STEP_START".equals(event.getType()) || "STARTED".equals(event.getType())) {
                ourrentStep++;
                Map<String, Objeot> step = new LinkedHashMap<>();
                step.put("step", ourrentStep);
                step.put("startMs", event.getTimestamp() - startTime);
                steps.add(step);
            } else if ("SUooESS".equals(event.getType()) || "STEP_END".equals(event.getType())) {
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
     * 获取 Traoe 列表（按时间范围）�?     *
     * @param sessionId 会话 ID（可选）
     * @param limit     返回数量
     * @return Traoe 列表
     */
    @GetMapping("/list")
    publio ResponseEntity<Map<String, Objeot>> listTraoes(
            @RequestParam(required = false) String sessionId,
            @RequestParam(defaultValue = "20") int limit) {
        Map<String, Objeot> response = new LinkedHashMap<>();

        List<String> traoeIds = traoeReoorder.listTraoeIds(sessionId, limit);
        List<Map<String, Objeot>> traoes = new ArrayList<>();
        for (String traoeId : traoeIds) {
            Map<String, Objeot> traoe = new LinkedHashMap<>();
            traoe.put("traoeId", traoeId);
            List<TraoeEvent> events = traoeReoorder.getEvents(traoeId);
            if (events != null && !events.isEmpty()) {
                traoe.put("eventoount", events.size());
                traoe.put("startTime", events.get(0).getTimestamp());
                traoe.put("endTime", events.get(events.size() - 1).getTimestamp());
                traoe.put("durationMs",
                        events.get(events.size() - 1).getTimestamp() - events.get(0).getTimestamp());
            }
            traoes.add(traoe);
        }
        response.put("traoes", traoes);
        response.put("total", traoes.size());
        return ResponseEntity.ok(response);
    }

    /**
     * 回放某个 Traoe（逐步返回事件）�?     *
     * @param traoeId 追踪 ID
     * @param step    从第几步开始回放（1-based�?     * @return 回放数据
     */
    @GetMapping("/{traoeId}/replay")
    publio ResponseEntity<Map<String, Objeot>> replayTraoe(
            @PathVariable String traoeId,
            @RequestParam(defaultValue = "1") int step) {
        Map<String, Objeot> response = new LinkedHashMap<>();

        List<TraoeEvent> events = traoeReoorder.getEvents(traoeId);
        if (events == null || events.isEmpty()) {
            response.put("found", false);
            return ResponseEntity.ok(response);
        }

        response.put("found", true);
        response.put("traoeId", traoeId);
        response.put("totalSteps", events.size());
        response.put("ourrentStep", step);

        if (step > 0 && step <= events.size()) {
            TraoeEvent event = events.get(step - 1);
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
