package com.njydsz.pmis.agent.controller.agent;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmis.agent.engine.AgentContext;
import com.njydsz.pmis.agent.engine.MultimodalInput;
import com.njydsz.pmis.agent.engine.llm.LlmProvider;
import com.njydsz.pmis.agent.engine.llm.LlmProviderRouter;
import com.njydsz.pmis.agent.engine.llm.ModelLoadBalancer;
import com.njydsz.pmis.agent.engine.llm.TokenUsage;
import com.njydsz.pmis.agent.engine.memory.ChatMemory;
import com.njydsz.pmis.agent.engine.memory.ChatMessage;
import com.njydsz.pmis.agent.engine.react.ReActLoop;
import com.njydsz.pmis.agent.engine.react.ReActResult;
import com.njydsz.pmis.agent.engine.stream.NoOpReActEventListener;
import com.njydsz.pmis.agent.engine.stream.ReActEventListener;
import com.njydsz.pmis.agent.engine.stream.StreamEvent;
import com.njydsz.pmis.agent.tool.AgentTool;
import com.njydsz.pmis.agent.tool.ToolRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Agent Playground 调试 API（P2-2 落地）。
 *
 * <p>对标 Coze Playground / Dify Debug / OpenAI Playground：
 * 提供在线调试 Agent 的 REST API，支持：
 * <ul>
 *   <li><b>单轮对话调试</b> - 快速验证 Agent 对特定输入的响应</li>
 *   <li><b>流式调试</b> - SSE 推送 ReAct 循环全过程（思考→工具调用→观察→答案）</li>
 *   <li><b>工具列表查询</b> - 查看当前注册的所有工具及其 schema</li>
 *   <li><b>模型状态查询</b> - 查看 LLM Provider 状态、熔断器状态、缓存命中率</li>
 *   <li><b>对话历史查询</b> - 按 sessionId 查看对话历史</li>
 *   <li><b>负载均衡统计</b> - 查看模型路由统计和延迟分布</li>
 * </ul>
 *
 * <p>所有接口均返回 JSON 格式，便于前端 Playground UI 消费。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0 (P2-2)
 */
@Slf4j
@RestController
@RequestMapping("/agent/playground")
@RequiredArgsConstructor
public class AgentPlaygroundController {

    private final ReActLoop reActLoop;
    private final LlmProviderRouter llmProviderRouter;
    private final ToolRegistry toolRegistry;
    private final ObjectProvider<ChatMemory> chatMemoryProvider;
    private final ObjectProvider<ModelLoadBalancer> loadBalancerProvider;

    private final ExecutorService sseExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "playground-sse");
        t.setDaemon(true);
        return t;
    });

    /**
     * 单轮对话调试。
     *
     * <p>快速验证 Agent 对特定输入的响应，返回完整的 ReAct 执行步骤和最终答案。
     *
     * @param request 调试请求
     * @return 调试结果
     */
    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody PlaygroundChatRequest request) {
        long startTime = System.currentTimeMillis();
        Map<String, Object> response = new LinkedHashMap<>();

        try {
            AgentContext ctx = buildContext(request);

            ReActResult result = reActLoop.runStream(
                    request.getSystemPrompt() != null ? request.getSystemPrompt()
                            : "你是一个智能项目管理助手。",
                    request.getUserInput(),
                    ctx,
                    request.getMaxSteps() != null ? request.getMaxSteps() : 10,
                    NoOpReActEventListener.getInstance());

            long costMs = System.currentTimeMillis() - startTime;
            response.put("success", result.isSuccess());
            response.put("finalAnswer", result.getFinalAnswer());
            response.put("totalSteps", result.getTotalSteps());
            response.put("costMs", costMs);

            // 步骤详情
            List<Map<String, Object>> steps = new ArrayList<>();
            if (result.getSteps() != null) {
                for (var step : result.getSteps()) {
                    Map<String, Object> stepMap = new LinkedHashMap<>();
                    stepMap.put("stepIndex", step.getStepIndex());
                    stepMap.put("thought", step.getThought());
                    stepMap.put("action", step.getAction());
                    stepMap.put("parameters", step.getParameters());
                    stepMap.put("observation", step.getObservation());
                    stepMap.put("finalAnswer", step.getFinalAnswer());
                    steps.add(stepMap);
                }
            }
            response.put("steps", steps);

            // Token 用量（P0-3）
            if (ctx.getTokenUsage() != null) {
                Map<String, Object> usage = new LinkedHashMap<>();
                TokenUsage tu = ctx.getTokenUsage();
                usage.put("promptTokens", tu.getPromptTokens());
                usage.put("completionTokens", tu.getCompletionTokens());
                usage.put("totalTokens", tu.getTotalTokens());
                usage.put("estimatedCostUsd", String.format("%.6f", tu.estimatedCostUsd()));
                response.put("tokenUsage", usage);
            }

            response.put("failureReason", result.getFailureReason());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("[Playground] 调试异常", e);
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("costMs", System.currentTimeMillis() - startTime);
            return ResponseEntity.ok(response);
        }
    }

    /**
     * 流式调试（SSE）。
     *
     * <p>推送 ReAct 循环全过程事件，让前端实时展示
     * 「思考中 → 调用工具 → 观察 → 最终回答」全过程。
     *
     * @param request 调试请求
     * @return SSE 流
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody PlaygroundChatRequest request) {
        SseEmitter emitter = new SseEmitter(300_000L); // 5 分钟超时

        sseExecutor.submit(() -> {
            try {
                AgentContext ctx = buildContext(request);

                ReActEventListener listener = new ReActEventListener() {
                    @Override
                    public void onStepStart(int stepIndex) {
                        sendEvent(emitter, StreamEvent.of(StreamEvent.Type.STEP_START, stepIndex));
                    }

                    @Override
                    public void onThought(int stepIndex, String thought) {
                        Map<String, Object> payload = Map.of("thought", thought);
                        sendEvent(emitter, StreamEvent.of(StreamEvent.Type.THOUGHT, stepIndex, payload));
                    }

                    @Override
                    public void onAction(int stepIndex, com.njydsz.pmis.agent.engine.react.ReActDecision decision) {
                        Map<String, Object> payload = new LinkedHashMap<>();
                        payload.put("action", decision.getAction());
                        payload.put("parameters", decision.getParameters());
                        sendEvent(emitter, StreamEvent.of(StreamEvent.Type.ACTION, stepIndex, payload));
                    }

                    @Override
                    public void onObservation(int stepIndex, String observation) {
                        Map<String, Object> payload = Map.of("observation", observation);
                        sendEvent(emitter, StreamEvent.of(StreamEvent.Type.OBSERVATION, stepIndex, payload));
                    }

                    @Override
                    public void onFinalAnswer(int stepIndex, String finalAnswer) {
                        Map<String, Object> payload = Map.of("finalAnswer", finalAnswer);
                        sendEvent(emitter, StreamEvent.of(StreamEvent.Type.FINAL_ANSWER, stepIndex, payload));
                    }

                    @Override
                    public void onStepEnd(int stepIndex) {
                        sendEvent(emitter, StreamEvent.of(StreamEvent.Type.STEP_END, stepIndex));
                    }

                    @Override
                    public void onComplete(ReActResult result) {
                        Map<String, Object> payload = new LinkedHashMap<>();
                        payload.put("success", result.isSuccess());
                        payload.put("totalSteps", result.getTotalSteps());
                        sendEvent(emitter, StreamEvent.done(result.getTotalSteps(), result.isSuccess()));
                        emitter.complete();
                    }

                    @Override
                    public void onError(int stepIndex, Throwable error) {
                        sendEvent(emitter, StreamEvent.error(stepIndex, error.getMessage()));
                        emitter.completeWithError(error);
                    }
                };

                reActLoop.runStream(
                        request.getSystemPrompt() != null ? request.getSystemPrompt()
                                : "你是一个智能项目管理助手。",
                        request.getUserInput(),
                        ctx,
                        request.getMaxSteps() != null ? request.getMaxSteps() : 10,
                        listener);

            } catch (Exception e) {
                log.error("[Playground] 流式调试异常", e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("ERROR")
                            .data(Map.of("error", e.getMessage())));
                } catch (Exception ignored) {
                }
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * 查询工具列表。
     *
     * @return 工具列表
     */
    @GetMapping("/tools")
    public ResponseEntity<Map<String, Object>> listTools() {
        Map<String, Object> response = new LinkedHashMap<>();
        List<Map<String, Object>> tools = new ArrayList<>();

        for (AgentTool tool : toolRegistry.listTools()) {
            Map<String, Object> toolInfo = new LinkedHashMap<>();
            toolInfo.put("name", tool.name());
            toolInfo.put("description", tool.description());
            toolInfo.put("requiresApproval", tool.requiresApproval());
            toolInfo.put("jsonSchema", tool.jsonSchema());
            tools.add(toolInfo);
        }

        response.put("tools", tools);
        response.put("total", tools.size());
        return ResponseEntity.ok(response);
    }

    /**
     * 查询模型状态。
     *
     * @return 模型状态信息
     */
    @GetMapping("/model/status")
    public ResponseEntity<Map<String, Object>> modelStatus() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("activeProvider", llmProviderRouter.getActiveProviderName());
        response.put("cacheHitRate", String.format("%.2f", llmProviderRouter.getCacheHitRate()));

        // 负载均衡统计
        ModelLoadBalancer balancer = loadBalancerProvider.getIfAvailable();
        if (balancer != null) {
            response.put("loadBalancerStats", balancer.getStats());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * 查询对话历史。
     *
     * @param sessionId 会话 ID
     * @return 对话历史
     */
    @GetMapping("/history/{sessionId}")
    public ResponseEntity<Map<String, Object>> history(@PathVariable String sessionId) {
        Map<String, Object> response = new LinkedHashMap<>();
        ChatMemory chatMemory = chatMemoryProvider.getIfAvailable();
        if (chatMemory == null) {
            response.put("messages", Collections.emptyList());
            response.put("note", "ChatMemory 未启用");
            return ResponseEntity.ok(response);
        }
        List<ChatMessage> messages = chatMemory.getHistory(sessionId);
        List<Map<String, Object>> msgList = new ArrayList<>();
        if (messages != null) {
            for (ChatMessage msg : messages) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("role", msg.getRole() != null ? msg.getRole().name().toLowerCase() : "user");
                m.put("content", msg.getContent());
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

    private AgentContext buildContext(PlaygroundChatRequest request) {
        AgentContext ctx = new AgentContext(
                "playground", "playground-" + UUID.randomUUID(), "playground",
                null, "playground-user", "playground", new HashMap<>());
        if (request.getSessionId() != null) {
            ctx.setSessionId(request.getSessionId());
        }
        // 多模态输入
        if (request.getImageUrl() != null || request.getImageBase64() != null) {
            MultimodalInput multimodal = new MultimodalInput();
            multimodal.setText(request.getUserInput());
            if (request.getImageUrl() != null) {
                multimodal.setImageUrls(List.of(request.getImageUrl()));
            }
            if (request.getImageBase64() != null) {
                multimodal.setImageBase64List(List.of(request.getImageBase64()));
            }
            ctx.setMultimodalInput(multimodal);
        }
        return ctx;
    }

    private void sendEvent(SseEmitter emitter, StreamEvent event) {
        try {
            emitter.send(SseEmitter.event()
                    .name(event.getType().name())
                    .data(JSON.toJSONString(event)));
        } catch (Exception e) {
            log.warn("[Playground] SSE 发送失败: {}", e.getMessage());
        }
    }

    // ==================== DTO ====================

    /**
     * Playground 对话请求。
     */
    @lombok.Data
    public static class PlaygroundChatRequest {
        /** 用户输入 */
        private String userInput;
        /** 系统提示词（可选） */
        private String systemPrompt;
        /** 会话 ID（可选，用于多轮对话） */
        private String sessionId;
        /** 最大循环次数（可选） */
        private Integer maxSteps;
        /** 图片 URL（可选，多模态） */
        private String imageUrl;
        /** 图片 Base64（可选，多模态） */
        private String imageBase64;
    }
}
