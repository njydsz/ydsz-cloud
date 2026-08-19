package com.njydsz.agent.domain.json;

import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.MessageContent;
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
 * <h3>多模态内容（Vision 模型）</h3>
 *
 * <p>当消息携带 {@link MessageContent} 时，{@code content} 字段序列化为数组格式（OpenAI Vision API 契约）：
 *
 * <pre>{@code
 * "content": [
 *   {"type":"text","text":"描述这张图片"},
 *   {"type":"image_url","image_url":{"url":"https://..."}}
 * ]
 * }</pre>
 *
 * <p>纯文本消息保持字符串格式，向后兼容非 Vision 模型。
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
    // 多模态内容（Vision 模型）：序列化为 content 数组格式
    if (msg.getMultimodalContent() != null && !msg.getMultimodalContent().isEmpty()) {
      serializeMultimodalContent(msg.getMultimodalContent(), out);
    } else {
      out.writeString(msg.getContent() != null ? msg.getContent() : "");
    }
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

  /**
   * 将多模态内容序列化为 OpenAI Vision API content 数组格式。
   *
   * <p>每个 ContentPart 转换为对应的 JSON 对象：文本类型为 {@code {"type":"text","text":"..."}}； 图片类型为 {@code
   * {"type":"image_url","image_url":{"url":"..."}}}。
   *
   * @param content 多模态内容值对象
   * @param out JSON 写入器
   */
  private void serializeMultimodalContent(MessageContent content, JSONWriter out) {
    out.write("[");
    boolean first = true;
    for (MessageContent.ContentPart part : content.getParts()) {
      if (!first) {
        out.write(",");
      }
      first = false;
      if (part.isText()) {
        out.write("{\"type\":\"text\",\"text\":");
        out.writeString(part.text());
        out.write("}");
      } else if (part.isImage()) {
        out.write("{\"type\":\"image_url\",\"image_url\":{\"url\":");
        out.writeString(part.imageUrl());
        out.write("}}");
      }
    }
    out.write("]");
  }
}
