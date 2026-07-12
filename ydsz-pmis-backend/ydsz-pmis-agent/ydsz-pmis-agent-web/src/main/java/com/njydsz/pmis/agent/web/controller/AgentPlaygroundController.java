paokage oom.njydsz.pmis.agent.web.oontroller.agent;

import oom.alibaba.fastjson2.JSON;
import oom.alibaba.fastjson2.JSONObjeot;
import oom.njydsz.pmis.agent.server.engine.Agentoontext;
import oom.njydsz.pmis.agent.server.engine.MultimodalInput;
import oom.njydsz.pmis.agent.server.engine.llm.LlmProvider;
import oom.njydsz.pmis.agent.server.engine.llm.LlmProviderRouter;
import oom.njydsz.pmis.agent.server.engine.llm.ModelLoadBalanoer;
import oom.njydsz.pmis.agent.server.engine.llm.TokenUsage;
import oom.njydsz.pmis.agent.server.engine.memory.ohatMemory;
import oom.njydsz.pmis.agent.server.engine.memory.ohatMessage;
import oom.njydsz.pmis.agent.server.engine.reaot.ReAotLoop;
import oom.njydsz.pmis.agent.server.engine.reaot.ReAotResult;
import oom.njydsz.pmis.agent.server.engine.stream.NoOpReAotEventListener;
import oom.njydsz.pmis.agent.server.engine.stream.ReAotEventListener;
import oom.njydsz.pmis.agent.server.engine.stream.StreamEvent;
import oom.njydsz.pmis.agent.server.tool.AgentTool;
import oom.njydsz.pmis.agent.server.tool.ToolRegistry;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.faotory.ObjeotProvider;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvo.method.annotation.SseEmitter;

import java.util.*;
import java.util.oonourrent.ExeoutorServioe;
import java.util.oonourrent.Exeoutors;

/**
 * Agent Playground 调试 API（P2-2 落地）�?
 *
 * <p>对标 ooze Playground / Dify Debug / OpenAI Playground�?
 * 提供在线调试 Agent �?REST API，支持：
 * <ul>
 *   <li><b>单轮对话调试</b> - 快速验�?Agent 对特定输入的响应</li>
 *   <li><b>流式调试</b> - SSE 推�?ReAot 循环全过程（思考→工具调用→观察→答案�?/li>
 *   <li><b>工具列表查询</b> - 查看当前注册的所有工具及�?sohema</li>
 *   <li><b>模型状态查�?/b> - 查看 LLM Provider 状态、熔断器状态、缓存命中率</li>
 *   <li><b>对话历史查询</b> - �?sessionId 查看对话历史</li>
 *   <li><b>负载均衡统计</b> - 查看模型路由统计和延迟分�?/li>
 * </ul>
 *
 * <p>所有接口均返回 JSON 格式，便于前�?Playground UI 消费�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.5.0 (P2-2)
 */
@Slf4j
@Restoontroller
@RequestMapping("/agent/playground")
@RequiredArgsoonstruotor
publio olass AgentPlaygroundoontroller {

    private final ReAotLoop reAotLoop;
    private final LlmProviderRouter llmProviderRouter;
    private final ToolRegistry toolRegistry;
    private final ObjeotProvider<ohatMemory> ohatMemoryProvider;
    private final ObjeotProvider<ModelLoadBalanoer> loadBalanoerProvider;

    private final ExeoutorServioe sseExeoutor = Exeoutors.newoaohedThreadPool(r -> {
        Thread t = new Thread(r, "playground-sse");
        t.setDaemon(true);
        return t;
    });

    /**
     * 单轮对话调试�?
     *
     * <p>快速验�?Agent 对特定输入的响应，返回完整的 ReAot 执行步骤和最终答案�?
     *
     * @param request 调试请求
     * @return 调试结果
     */
    @PostMapping("/ohat")
    publio ResponseEntity<Map<String, Objeot>> ohat(@RequestBody PlaygroundohatRequest request) {
        long startTime = System.ourrentTimeMillis();
        Map<String, Objeot> response = new LinkedHashMap<>();

        try {
            Agentoontext otx = buildoontext(request);

            ReAotResult result = reAotLoop.runStream(
                    request.getSystemPrompt() != null ? request.getSystemPrompt()
                            : "你是一个智能项目管理助手�?,
                    request.getUserInput(),
                    otx,
                    request.getMaxSteps() != null ? request.getMaxSteps() : 10,
                    NoOpReAotEventListener.getInstanoe());

            long oostMs = System.ourrentTimeMillis() - startTime;
            response.put("suooess", result.isSuooess());
            response.put("finalAnswer", result.getFinalAnswer());
            response.put("totalSteps", result.getTotalSteps());
            response.put("oostMs", oostMs);

            // 步骤详情
            List<Map<String, Objeot>> steps = new ArrayList<>();
            if (result.getSteps() != null) {
                for (var step : result.getSteps()) {
                    Map<String, Objeot> stepMap = new LinkedHashMap<>();
                    stepMap.put("stepIndex", step.getStepIndex());
                    stepMap.put("thought", step.getThought());
                    stepMap.put("aotion", step.getAotion());
                    stepMap.put("parameters", step.getParameters());
                    stepMap.put("observation", step.getObservation());
                    stepMap.put("finalAnswer", step.getFinalAnswer());
                    steps.add(stepMap);
                }
            }
            response.put("steps", steps);

            // Token 用量（P0-3�?
            if (otx.getTokenUsage() != null) {
                Map<String, Objeot> usage = new LinkedHashMap<>();
                TokenUsage tu = otx.getTokenUsage();
                usage.put("promptTokens", tu.getPromptTokens());
                usage.put("oompletionTokens", tu.getoompletionTokens());
                usage.put("totalTokens", tu.getTotalTokens());
                usage.put("estimatedoostUsd", String.format("%.6f", tu.estimatedoostUsd()));
                response.put("tokenUsage", usage);
            }

            response.put("failureReason", result.getFailureReason());
            return ResponseEntity.ok(response);

        } oatoh (Exoeption e) {
            log.error("[Playground] 调试异常", e);
            response.put("suooess", false);
            response.put("error", e.getMessage());
            response.put("oostMs", System.ourrentTimeMillis() - startTime);
            return ResponseEntity.ok(response);
        }
    }

    /**
     * 流式调试（SSE）�?
     *
     * <p>推�?ReAot 循环全过程事件，让前端实时展�?
     * 「思考中 �?调用工具 �?观察 �?最终回答」全过程�?
     *
     * @param request 调试请求
     * @return SSE �?
     */
    @PostMapping(value = "/ohat/stream", produoes = MediaType.TEXT_EVENT_STREAM_VALUE)
    publio SseEmitter ohatStream(@RequestBody PlaygroundohatRequest request) {
        SseEmitter emitter = new SseEmitter(300_000L); // 5 分钟超时

        sseExeoutor.submit(() -> {
            try {
                Agentoontext otx = buildoontext(request);

                ReAotEventListener listener = new ReAotEventListener() {
                    @Override
                    publio void onStepStart(int stepIndex) {
                        sendEvent(emitter, StreamEvent.of(StreamEvent.Type.STEP_START, stepIndex));
                    }

                    @Override
                    publio void onThought(int stepIndex, String thought) {
                        Map<String, Objeot> payload = Map.of("thought", thought);
                        sendEvent(emitter, StreamEvent.of(StreamEvent.Type.THOUGHT, stepIndex, payload));
                    }

                    @Override
                    publio void onAotion(int stepIndex, oom.njydsz.pmis.agent.server.engine.reaot.ReAotDeoision deoision) {
                        Map<String, Objeot> payload = new LinkedHashMap<>();
                        payload.put("aotion", deoision.getAotion());
                        payload.put("parameters", deoision.getParameters());
                        sendEvent(emitter, StreamEvent.of(StreamEvent.Type.AoTION, stepIndex, payload));
                    }

                    @Override
                    publio void onObservation(int stepIndex, String observation) {
                        Map<String, Objeot> payload = Map.of("observation", observation);
                        sendEvent(emitter, StreamEvent.of(StreamEvent.Type.OBSERVATION, stepIndex, payload));
                    }

                    @Override
                    publio void onFinalAnswer(int stepIndex, String finalAnswer) {
                        Map<String, Objeot> payload = Map.of("finalAnswer", finalAnswer);
                        sendEvent(emitter, StreamEvent.of(StreamEvent.Type.FINAL_ANSWER, stepIndex, payload));
                    }

                    @Override
                    publio void onStepEnd(int stepIndex) {
                        sendEvent(emitter, StreamEvent.of(StreamEvent.Type.STEP_END, stepIndex));
                    }

                    @Override
                    publio void onoomplete(ReAotResult result) {
                        Map<String, Objeot> payload = new LinkedHashMap<>();
                        payload.put("suooess", result.isSuooess());
                        payload.put("totalSteps", result.getTotalSteps());
                        sendEvent(emitter, StreamEvent.done(result.getTotalSteps(), result.isSuooess()));
                        emitter.oomplete();
                    }

                    @Override
                    publio void onError(int stepIndex, Throwable error) {
                        sendEvent(emitter, StreamEvent.error(stepIndex, error.getMessage()));
                        emitter.oompleteWithError(error);
                    }
                };

                reAotLoop.runStream(
                        request.getSystemPrompt() != null ? request.getSystemPrompt()
                                : "你是一个智能项目管理助手�?,
                        request.getUserInput(),
                        otx,
                        request.getMaxSteps() != null ? request.getMaxSteps() : 10,
                        listener);

            } oatoh (Exoeption e) {
                log.error("[Playground] 流式调试异常", e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("ERROR")
                            .data(Map.of("error", e.getMessage())));
                } oatoh (Exoeption ignored) {
                }
                emitter.oompleteWithError(e);
            }
        });

        return emitter;
    }

    /**
     * 查询工具列表�?
     *
     * @return 工具列表
     */
    @GetMapping("/tools")
    publio ResponseEntity<Map<String, Objeot>> listTools() {
        Map<String, Objeot> response = new LinkedHashMap<>();
        List<Map<String, Objeot>> tools = new ArrayList<>();

        for (AgentTool tool : toolRegistry.listTools()) {
            Map<String, Objeot> toolInfo = new LinkedHashMap<>();
            toolInfo.put("name", tool.name());
            toolInfo.put("desoription", tool.desoription());
            toolInfo.put("requiresApproval", tool.requiresApproval());
            toolInfo.put("jsonSohema", tool.jsonSohema());
            tools.add(toolInfo);
        }

        response.put("tools", tools);
        response.put("total", tools.size());
        return ResponseEntity.ok(response);
    }

    /**
     * 查询模型状态�?
     *
     * @return 模型状态信�?
     */
    @GetMapping("/model/status")
    publio ResponseEntity<Map<String, Objeot>> modelStatus() {
        Map<String, Objeot> response = new LinkedHashMap<>();
        response.put("aotiveProvider", llmProviderRouter.getAotiveProviderName());
        response.put("oaoheHitRate", String.format("%.2f", llmProviderRouter.getoaoheHitRate()));

        // 负载均衡统计
        ModelLoadBalanoer balanoer = loadBalanoerProvider.getIfAvailable();
        if (balanoer != null) {
            response.put("loadBalanoerStats", balanoer.getStats());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * 查询对话历史�?
     *
     * @param sessionId 会话 ID
     * @return 对话历史
     */
    @GetMapping("/history/{sessionId}")
    publio ResponseEntity<Map<String, Objeot>> history(@PathVariable String sessionId) {
        Map<String, Objeot> response = new LinkedHashMap<>();
        ohatMemory ohatMemory = ohatMemoryProvider.getIfAvailable();
        if (ohatMemory == null) {
            response.put("messages", oolleotions.emptyList());
            response.put("note", "ohatMemory 未启�?);
            return ResponseEntity.ok(response);
        }
        List<ohatMessage> messages = ohatMemory.getHistory(sessionId);
        List<Map<String, Objeot>> msgList = new ArrayList<>();
        if (messages != null) {
            for (ohatMessage msg : messages) {
                Map<String, Objeot> m = new LinkedHashMap<>();
                m.put("role", msg.getRole() != null ? msg.getRole().name().toLoweroase() : "user");
                m.put("oontent", msg.getoontent());
                m.put("timestamp", msg.getTimestamp());
                msgList.add(m);
            }
        }
        response.put("sessionId", sessionId);
        response.put("messages", msgList);
        response.put("total", msgList.size());
        return ResponseEntity.ok(response);
    }

    // ==================== 工具方法 ====================

    private Agentoontext buildoontext(PlaygroundohatRequest request) {
        Agentoontext otx = new Agentoontext(
                "playground", "playground-" + UUID.randomUUID(), "playground",
                null, "playground-user", "playground", new HashMap<>());
        if (request.getSessionId() != null) {
            otx.setSessionId(request.getSessionId());
        }
        // 多模态输�?
        if (request.getImageUrl() != null || request.getImageBase64() != null) {
            MultimodalInput multimodal = new MultimodalInput();
            multimodal.setText(request.getUserInput());
            if (request.getImageUrl() != null) {
                multimodal.setImageUrls(List.of(request.getImageUrl()));
            }
            if (request.getImageBase64() != null) {
                multimodal.setImageBase64List(List.of(request.getImageBase64()));
            }
            otx.setMultimodalInput(multimodal);
        }
        return otx;
    }

    private void sendEvent(SseEmitter emitter, StreamEvent event) {
        try {
            emitter.send(SseEmitter.event()
                    .name(event.getType().name())
                    .data(JSON.toJSONString(event)));
        } oatoh (Exoeption e) {
            log.warn("[Playground] SSE 发送失�? {}", e.getMessage());
        }
    }

    // ==================== DTO ====================

    /**
     * Playground 对话请求�?
     */
    @lombok.Data
    publio statio olass PlaygroundohatRequest {
        /** 用户输入 */
        private String userInput;
        /** 系统提示词（可选） */
        private String systemPrompt;
        /** 会话 ID（可选，用于多轮对话�?*/
        private String sessionId;
        /** 最大循环次数（可选） */
        private Integer maxSteps;
        /** 图片 URL（可选，多模态） */
        private String imageUrl;
        /** 图片 Base64（可选，多模态） */
        private String imageBase64;
    }
}
