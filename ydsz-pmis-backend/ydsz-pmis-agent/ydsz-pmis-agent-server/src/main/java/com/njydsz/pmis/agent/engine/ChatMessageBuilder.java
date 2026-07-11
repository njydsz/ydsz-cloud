package com.njydsz.pmis.agent.server.engine.llm;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmis.agent.server.engine.AgentContext;
import com.njydsz.pmis.agent.server.engine.MultimodalInput;
import com.njydsz.pmis.agent.server.engine.memory.ChatMessage;
import com.njydsz.pmis.agent.server.engine.memory.ChatMemory;
import com.njydsz.pmis.agent.server.engine.llm.LlmToolCallResponse.ToolCall;

import java.util.ArrayList;
import java.util.List;

/**
 * 结构化消息列表构建器（P0-2 落地）。
 *
 * <p>对标 OpenAI Chat Completions API 的 messages 数组格式，
 * 替代原有的纯文本拼接方式，让 LLM API 原生理解对话上下文。
 *
 * <p>消息格式：
 * <pre>
 * [
 *   {"role":"system","content":"你是一个助手..."},
 *   {"role":"user","content":"你好"},
 *   {"role":"assistant","content":"你好，有什么可以帮你的？"},
 *   {"role":"assistant","tool_calls":[{"id":"call_1","type":"function","function":{"name":"search","arguments":"{...}"}}]},
 *   {"role":"tool","tool_call_id":"call_1","content":"搜索结果..."},
 *   {"role":"user","content":"继续"}
 * ]
 * </pre>
 *
 * <p>与纯文本拼接相比的优势：
 * <ul>
 *   <li>System prompt 与历史消息边界清晰，LLM 能正确区分角色</li>
 *   <li>Token 节省：无需每轮重复传输角色前缀文本</li>
 *   <li>支持 OpenAI tool role 消息传递工具调用结果</li>
 *   <li>支持多模态 content 数组（图片+文本混合）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.5.0 (P0-2)
 */
public class ChatMessageBuilder {

    private final List<JSONObject> messages = new ArrayList<>();

    /**
     * 添加 system 消息。
     *
     * @param content 系统提示词
     * @return this（链式调用）
     */
    public ChatMessageBuilder system(String content) {
        if (content != null && !content.isBlank()) {
            JSONObject msg = new JSONObject();
            msg.put("role", "system");
            msg.put("content", content);
            messages.add(msg);
        }
        return this;
    }

    /**
     * 添加 user 消息（纯文本）。
     *
     * @param content 用户输入
     * @return this
     */
    public ChatMessageBuilder user(String content) {
        if (content != null && !content.isBlank()) {
            JSONObject msg = new JSONObject();
            msg.put("role", "user");
            msg.put("content", content);
            messages.add(msg);
        }
        return this;
    }

    /**
     * 添加 user 消息（多模态，P1-5 落地）。
     *
     * <p>当 AgentContext 携带 MultimodalInput 时，构造 OpenAI Vision 格式的
     * content 数组，支持图片+文本混合输入。
     *
     * @param text     文本内容
     * @param multimodal 多模态输入
     * @return this
     */
    public ChatMessageBuilder userMultimodal(String text, MultimodalInput multimodal) {
        JSONObject msg = new JSONObject();
        msg.put("role", "user");

        if (multimodal != null && multimodal.hasMultimodalContent()) {
            // 多模态：构造 content 数组
            JSONArray contentArr = new JSONArray();
            if (text != null && !text.isBlank()) {
                JSONObject textPart = new JSONObject();
                textPart.put("type", "text");
                textPart.put("text", text);
                contentArr.add(textPart);
            }
            if (multimodal.getImageUrls() != null) {
                for (String url : multimodal.getImageUrls()) {
                    JSONObject imgPart = new JSONObject();
                    imgPart.put("type", "image_url");
                    JSONObject imgUrl = new JSONObject();
                    imgUrl.put("url", url);
                    imgPart.put("image_url", imgUrl);
                    contentArr.add(imgPart);
                }
            }
            if (multimodal.getImageBase64List() != null) {
                for (String base64 : multimodal.getImageBase64List()) {
                    JSONObject imgPart = new JSONObject();
                    imgPart.put("type", "image_url");
                    JSONObject imgUrl = new JSONObject();
                    imgUrl.put("url", base64);
                    imgPart.put("image_url", imgUrl);
                    contentArr.add(imgPart);
                }
            }
            msg.put("content", contentArr);
        } else {
            msg.put("content", text == null ? "" : text);
        }
        messages.add(msg);
        return this;
    }

    /**
     * 添加 assistant 消息（纯文本回复）。
     *
     * @param content 助手回复
     * @return this
     */
    public ChatMessageBuilder assistant(String content) {
        JSONObject msg = new JSONObject();
        msg.put("role", "assistant");
        msg.put("content", content == null ? "" : content);
        messages.add(msg);
        return this;
    }

    /**
     * 添加 assistant 消息（带工具调用请求）。
     *
     * <p>对标 OpenAI 格式：assistant 消息可以携带 tool_calls 字段，
     * 表示 LLM 请求调用工具。
     *
     * @param content    助手文本（可为空）
     * @param toolCalls  工具调用列表
     * @return this
     */
    public ChatMessageBuilder assistantWithToolCalls(String content, List<ToolCall> toolCalls) {
        JSONObject msg = new JSONObject();
        msg.put("role", "assistant");
        if (content != null && !content.isBlank()) {
            msg.put("content", content);
        }
        if (toolCalls != null && !toolCalls.isEmpty()) {
            JSONArray tcArr = new JSONArray();
            for (ToolCall tc : toolCalls) {
                JSONObject tcJson = new JSONObject();
                tcJson.put("id", tc.getId());
                tcJson.put("type", tc.getType() != null ? tc.getType() : "function");
                if (tc.getFunction() != null) {
                    JSONObject fn = new JSONObject();
                    fn.put("name", tc.getFunction().getName());
                    fn.put("arguments", tc.getFunction().getArguments() != null
                            ? tc.getFunction().getArguments() : "{}");
                    tcJson.put("function", fn);
                }
                tcArr.add(tcJson);
            }
            msg.put("tool_calls", tcArr);
        }
        messages.add(msg);
        return this;
    }

    /**
     * 添加 tool 消息（工具执行结果回传）。
     *
     * <p>对标 OpenAI 格式：工具执行结果以 role=tool 消息回传，
     * 携带 tool_call_id 关联到对应的工具调用请求。
     *
     * @param toolCallId 工具调用 ID
     * @param content    工具执行结果
     * @return this
     */
    public ChatMessageBuilder tool(String toolCallId, String content) {
        JSONObject msg = new JSONObject();
        msg.put("role", "tool");
        msg.put("tool_call_id", toolCallId);
        msg.put("content", content == null ? "" : content);
        messages.add(msg);
        return this;
    }

    /**
     * 从 ChatMemory 加载历史对话并添加到消息列表（P0-2 结构化历史传递）。
     *
     * @param chatMemory 对话记忆
     * @param sessionId  会话 ID
     * @return this
     */
    public ChatMessageBuilder history(ChatMemory chatMemory, String sessionId) {
        if (chatMemory == null || sessionId == null || sessionId.isBlank()) {
            return this;
        }
        try {
            List<ChatMessage> history = chatMemory.getHistory(sessionId);
            if (history == null || history.isEmpty()) {
                return this;
            }
            for (ChatMessage msg : history) {
                if (msg == null || msg.getContent() == null) continue;
                String role = msg.getRole() == null ? "user" : msg.getRole().name().toLowerCase();
                JSONObject jsonMsg = new JSONObject();
                jsonMsg.put("role", role);
                jsonMsg.put("content", msg.getContent());
                messages.add(jsonMsg);
            }
        } catch (Exception e) {
            // 历史加载失败不影响主流程
        }
        return this;
    }

    /**
     * 构建消息列表。
     *
     * @return JSON 数组格式的消息列表
     */
    public List<JSONObject> build() {
        return new ArrayList<>(messages);
    }

    /**
     * 构建 JSON 字符串。
     *
     * @return messages 数组的 JSON 字符串
     */
    public String toJsonString() {
        JSONArray arr = new JSONArray();
        for (JSONObject msg : messages) {
            arr.add(msg);
        }
        return arr.toJSONString();
    }

    /**
     * 构建完整的 user prompt（降级兼容，当 Provider 不支持 messages 数组时使用）。
     *
     * <p>将 system + 历史消息拼接为纯文本格式，保持向后兼容。
     *
     * @return 拼接后的 prompt 文本
     */
    public String toFlatText() {
        StringBuilder sb = new StringBuilder();
        for (JSONObject msg : messages) {
            String role = msg.getString("role");
            String content = msg.containsKey("content")
                    ? (msg.get("content") instanceof String
                        ? msg.getString("content")
                        : msg.getJSONArray("content").toJSONString())
                    : "";
            if ("system".equals(role)) {
                sb.append("[System]\n").append(content).append("\n\n");
            } else if ("user".equals(role)) {
                sb.append("[User]\n").append(content).append("\n\n");
            } else if ("assistant".equals(role)) {
                sb.append("[Assistant]\n").append(content).append("\n\n");
            } else if ("tool".equals(role)) {
                sb.append("[Tool Result]\n").append(content).append("\n\n");
            }
        }
        return sb.toString().trim();
    }
}
