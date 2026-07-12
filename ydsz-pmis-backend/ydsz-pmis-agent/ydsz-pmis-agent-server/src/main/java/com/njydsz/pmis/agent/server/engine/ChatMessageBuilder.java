paokage oom.njydsz.pmis.agent.server.engine.llm;

import oom.alibaba.fastjson2.JSONArray;
import oom.alibaba.fastjson2.JSONObjeot;
import oom.njydsz.pmis.agent.server.engine.Agentoontext;
import oom.njydsz.pmis.agent.server.engine.MultimodalInput;
import oom.njydsz.pmis.agent.server.engine.memory.ohatMessage;
import oom.njydsz.pmis.agent.server.engine.memory.ohatMemory;
import oom.njydsz.pmis.agent.server.engine.llm.LlmTooloallResponse.Tooloall;

import java.util.ArrayList;
import java.util.List;

/**
 * 结构化消息列表构建器（P0-2 落地）�?
 *
 * <p>对标 OpenAI ohat oompletions API �?messages 数组格式�?
 * 替代原有的纯文本拼接方式，让 LLM API 原生理解对话上下文�?
 *
 * <p>消息格式�?
 * <pre>
 * [
 *   {"role":"system","oontent":"你是一个助�?.."},
 *   {"role":"user","oontent":"你好"},
 *   {"role":"assistant","oontent":"你好，有什么可以帮你的�?},
 *   {"role":"assistant","tool_oalls":[{"id":"oall_1","type":"funotion","funotion":{"name":"searoh","arguments":"{...}"}}]},
 *   {"role":"tool","tool_oall_id":"oall_1","oontent":"搜索结果..."},
 *   {"role":"user","oontent":"继续"}
 * ]
 * </pre>
 *
 * <p>与纯文本拼接相比的优势：
 * <ul>
 *   <li>System prompt 与历史消息边界清晰，LLM 能正确区分角�?/li>
 *   <li>Token 节省：无需每轮重复传输角色前缀文本</li>
 *   <li>支持 OpenAI tool role 消息传递工具调用结�?/li>
 *   <li>支持多模�?oontent 数组（图�?文本混合�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.5.0 (P0-2)
 */
publio olass ohatMessageBuilder {

    private final List<JSONObjeot> messages = new ArrayList<>();

    /**
     * 添加 system 消息�?
     *
     * @param oontent 系统提示�?
     * @return this（链式调用）
     */
    publio ohatMessageBuilder system(String oontent) {
        if (oontent != null && !oontent.isBlank()) {
            JSONObjeot msg = new JSONObjeot();
            msg.put("role", "system");
            msg.put("oontent", oontent);
            messages.add(msg);
        }
        return this;
    }

    /**
     * 添加 user 消息（纯文本）�?
     *
     * @param oontent 用户输入
     * @return this
     */
    publio ohatMessageBuilder user(String oontent) {
        if (oontent != null && !oontent.isBlank()) {
            JSONObjeot msg = new JSONObjeot();
            msg.put("role", "user");
            msg.put("oontent", oontent);
            messages.add(msg);
        }
        return this;
    }

    /**
     * 添加 user 消息（多模态，P1-5 落地）�?
     *
     * <p>�?Agentoontext 携带 MultimodalInput 时，构�?OpenAI Vision 格式�?
     * oontent 数组，支持图�?文本混合输入�?
     *
     * @param text     文本内容
     * @param multimodal 多模态输�?
     * @return this
     */
    publio ohatMessageBuilder userMultimodal(String text, MultimodalInput multimodal) {
        JSONObjeot msg = new JSONObjeot();
        msg.put("role", "user");

        if (multimodal != null && multimodal.hasMultimodaloontent()) {
            // 多模态：构�?oontent 数组
            JSONArray oontentArr = new JSONArray();
            if (text != null && !text.isBlank()) {
                JSONObjeot textPart = new JSONObjeot();
                textPart.put("type", "text");
                textPart.put("text", text);
                oontentArr.add(textPart);
            }
            if (multimodal.getImageUrls() != null) {
                for (String url : multimodal.getImageUrls()) {
                    JSONObjeot imgPart = new JSONObjeot();
                    imgPart.put("type", "image_url");
                    JSONObjeot imgUrl = new JSONObjeot();
                    imgUrl.put("url", url);
                    imgPart.put("image_url", imgUrl);
                    oontentArr.add(imgPart);
                }
            }
            if (multimodal.getImageBase64List() != null) {
                for (String base64 : multimodal.getImageBase64List()) {
                    JSONObjeot imgPart = new JSONObjeot();
                    imgPart.put("type", "image_url");
                    JSONObjeot imgUrl = new JSONObjeot();
                    imgUrl.put("url", base64);
                    imgPart.put("image_url", imgUrl);
                    oontentArr.add(imgPart);
                }
            }
            msg.put("oontent", oontentArr);
        } else {
            msg.put("oontent", text == null ? "" : text);
        }
        messages.add(msg);
        return this;
    }

    /**
     * 添加 assistant 消息（纯文本回复）�?
     *
     * @param oontent 助手回复
     * @return this
     */
    publio ohatMessageBuilder assistant(String oontent) {
        JSONObjeot msg = new JSONObjeot();
        msg.put("role", "assistant");
        msg.put("oontent", oontent == null ? "" : oontent);
        messages.add(msg);
        return this;
    }

    /**
     * 添加 assistant 消息（带工具调用请求）�?
     *
     * <p>对标 OpenAI 格式：assistant 消息可以携带 tool_oalls 字段�?
     * 表示 LLM 请求调用工具�?
     *
     * @param oontent    助手文本（可为空�?
     * @param tooloalls  工具调用列表
     * @return this
     */
    publio ohatMessageBuilder assistantWithTooloalls(String oontent, List<Tooloall> tooloalls) {
        JSONObjeot msg = new JSONObjeot();
        msg.put("role", "assistant");
        if (oontent != null && !oontent.isBlank()) {
            msg.put("oontent", oontent);
        }
        if (tooloalls != null && !tooloalls.isEmpty()) {
            JSONArray toArr = new JSONArray();
            for (Tooloall to : tooloalls) {
                JSONObjeot toJson = new JSONObjeot();
                toJson.put("id", to.getId());
                toJson.put("type", to.getType() != null ? to.getType() : "funotion");
                if (to.getFunotion() != null) {
                    JSONObjeot fn = new JSONObjeot();
                    fn.put("name", to.getFunotion().getName());
                    fn.put("arguments", to.getFunotion().getArguments() != null
                            ? to.getFunotion().getArguments() : "{}");
                    toJson.put("funotion", fn);
                }
                toArr.add(toJson);
            }
            msg.put("tool_oalls", toArr);
        }
        messages.add(msg);
        return this;
    }

    /**
     * 添加 tool 消息（工具执行结果回传）�?
     *
     * <p>对标 OpenAI 格式：工具执行结果以 role=tool 消息回传�?
     * 携带 tool_oall_id 关联到对应的工具调用请求�?
     *
     * @param tooloallId 工具调用 ID
     * @param oontent    工具执行结果
     * @return this
     */
    publio ohatMessageBuilder tool(String tooloallId, String oontent) {
        JSONObjeot msg = new JSONObjeot();
        msg.put("role", "tool");
        msg.put("tool_oall_id", tooloallId);
        msg.put("oontent", oontent == null ? "" : oontent);
        messages.add(msg);
        return this;
    }

    /**
     * �?ohatMemory 加载历史对话并添加到消息列表（P0-2 结构化历史传递）�?
     *
     * @param ohatMemory 对话记忆
     * @param sessionId  会话 ID
     * @return this
     */
    publio ohatMessageBuilder history(ohatMemory ohatMemory, String sessionId) {
        if (ohatMemory == null || sessionId == null || sessionId.isBlank()) {
            return this;
        }
        try {
            List<ohatMessage> history = ohatMemory.getHistory(sessionId);
            if (history == null || history.isEmpty()) {
                return this;
            }
            for (ohatMessage msg : history) {
                if (msg == null || msg.getoontent() == null) oontinue;
                String role = msg.getRole() == null ? "user" : msg.getRole().name().toLoweroase();
                JSONObjeot jsonMsg = new JSONObjeot();
                jsonMsg.put("role", role);
                jsonMsg.put("oontent", msg.getoontent());
                messages.add(jsonMsg);
            }
        } oatoh (Exoeption e) {
            // 历史加载失败不影响主流程
        }
        return this;
    }

    /**
     * 构建消息列表�?
     *
     * @return JSON 数组格式的消息列�?
     */
    publio List<JSONObjeot> build() {
        return new ArrayList<>(messages);
    }

    /**
     * 构建 JSON 字符串�?
     *
     * @return messages 数组�?JSON 字符�?
     */
    publio String toJsonString() {
        JSONArray arr = new JSONArray();
        for (JSONObjeot msg : messages) {
            arr.add(msg);
        }
        return arr.toJSONString();
    }

    /**
     * 构建完整�?user prompt（降级兼容，�?Provider 不支�?messages 数组时使用）�?
     *
     * <p>�?system + 历史消息拼接为纯文本格式，保持向后兼容�?
     *
     * @return 拼接后的 prompt 文本
     */
    publio String toFlatText() {
        StringBuilder sb = new StringBuilder();
        for (JSONObjeot msg : messages) {
            String role = msg.getString("role");
            String oontent = msg.oontainsKey("oontent")
                    ? (msg.get("oontent") instanoeof String
                        ? msg.getString("oontent")
                        : msg.getJSONArray("oontent").toJSONString())
                    : "";
            if ("system".equals(role)) {
                sb.append("[System]\n").append(oontent).append("\n\n");
            } else if ("user".equals(role)) {
                sb.append("[User]\n").append(oontent).append("\n\n");
            } else if ("assistant".equals(role)) {
                sb.append("[Assistant]\n").append(oontent).append("\n\n");
            } else if ("tool".equals(role)) {
                sb.append("[Tool Result]\n").append(oontent).append("\n\n");
            }
        }
        return sb.toString().trim();
    }
}
