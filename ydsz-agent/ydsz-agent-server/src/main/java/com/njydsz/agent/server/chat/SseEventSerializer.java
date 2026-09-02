package com.njydsz.agent.server.chat;

import com.njydsz.agent.domain.model.SseEvent;
import com.njydsz.common.json.YdszJson;

/**
 * SSE 事件序列化器
 *
 * <p>将 {@link SseEvent} 序列化为标准 Server-Sent Events 格式：
 *
 * <pre>
 * event: message
 * data: {"content":"你好"}
 *
 * </pre>
 *
 * <p>遵循 <a href="https://html.spec.whatwg.org/multipage/server-sent-events.html">WHATWG SSE 规范</a>，
 * 每个事件以两个换行符（{@code \n\n}）结尾。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class SseEventSerializer {

  /** SSE 行结束符（规范要求 \r\n，但 \n 在绝大多数客户端也兼容） */
  private static final String LINE_END = "\n";

  /** 序列化缓冲区初始容量 */
  private static final int BUFFER_INITIAL_CAPACITY = 128;

  /** 禁止实例化工具类 */
  private SseEventSerializer() {}

  /**
   * 将 SSE 事件序列化为符合规范的字符串
   *
   * <p>输出格式：
   *
   * <pre>
   * event: {eventType}
   * data: {jsonEncodedData}
   *
   * </pre>
   *
   * @param event SSE 事件
   * @return 可直接写入 {@code OutputStream} 的 SSE 格式字符串
   */
  public static String serialize(SseEvent event) {
    if (event == null) {
      return "";
    }
    StringBuilder sb = new StringBuilder(BUFFER_INITIAL_CAPACITY);
    sb.append("event: ").append(event.getEvent()).append(LINE_END);
    sb.append("data: ").append(YdszJson.toJson(event.getData())).append(LINE_END);
    sb.append(LINE_END);
    return sb.toString();
  }

  /**
   * 序列化并直接写入 StringBuilder（批量场景减少中间字符串）
   *
   * @param event SSE 事件
   * @param builder 目标 StringBuilder
   */
  public static void serializeTo(SseEvent event, StringBuilder builder) {
    if (event == null) {
      return;
    }
    builder.append("event: ").append(event.getEvent()).append(LINE_END);
    builder.append("data: ").append(YdszJson.toJson(event.getData())).append(LINE_END);
    builder.append(LINE_END);
  }
}
