package com.njydsz.agent.domain.json;

import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.ToolCall;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.json.serializer.JsonSerializer;
import com.njydsz.common.json.writer.JSONWriter;

/**
 * {@link ChatMessage} 的 YdszJson 自定义序列化器（JsonModule SPI 落地 + OpenAI message 形状）。
 *
 * <p>产出 OpenAI message 结构：{@code
 * {"role":<apiValue>,"content":..,"tool_calls":[...]?,"tool_call_id":..?}}。 仅输出 LLM API 需要的字段（role
 * / content / tool_calls / tool_call_id）， 不包含 {@code createdAt} / {@code conversationId} / {@code
 * tokenUsage} 等内部字段。 嵌套 {@link ToolCall} 通过 {@link YdszJson#toJson(Object)} 委托给 {@link
 * ToolCallSerializer}， 走全局引擎路径后自动命中已注册的自定义序列化器。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class ChatMessageSerializer implements JsonSerializer<ChatMessage> {

  @Override
  public void serialize(ChatMessage msg, JSONWriter out) {
    if (msg == null) {
      out.write("null");
      return;
    }
    out.write("{\"role\":");
    out.writeString(msg.getRole().getApiValue());
    out.write(",\"content\":");
    out.writeString(msg.getContent() != null ? msg.getContent() : "");
    if (msg.hasToolCalls()) {
      out.write(",\"tool_calls\":[");
      boolean first = true;
      for (ToolCall tc : msg.getToolCalls()) {
        if (!first) {
          out.write(",");
        }
        first = false;
        // 委托全局引擎 -> 命中 ToolCallSerializer
        out.write(YdszJson.toJson(tc));
      }
      out.write("]");
    }
    if (msg.getToolCallId() != null) {
      out.write(",\"tool_call_id\":");
      out.writeString(msg.getToolCallId());
    }
    out.write("}");
  }
}
