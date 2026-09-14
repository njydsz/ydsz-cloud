package com.njydsz.agent.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link SseEvent} 单元测试。
 *
 * <p>覆盖事件工厂方法、source 字段传播与 payload 序列化语义。
 *
 * @author ydsz-team
 * @since 26.09.14
 */
class SseEventTest {

  /**
   * message 工厂应生成 message 类型事件。
   */
  @Test
  @DisplayName("message 工厂生成正确事件类型与内容")
  void messageFactoryShouldCreateMessageEvent() {
    SseEvent event = SseEvent.message("hello");

    assertEquals(SseEvent.EVENT_MESSAGE, event.getEvent());
    assertEquals("hello", event.getData().get("content"));
    assertNull(event.getSource());
    assertFalse(event.hasSource());
  }

  /**
   * tool 事件应携带工具名与参数信息。
   */
  @Test
  @DisplayName("toolCallStarted 工厂生成工具调用事件")
  void toolCallStartedFactoryShouldCreateToolEvent() {
    Map<String, Object> args = Map.of("query", "test");
    SseEvent event = SseEvent.toolCallStarted("search", args);

    assertEquals(SseEvent.EVENT_TOOL_CALL_STARTED, event.getEvent());
    assertEquals("search", event.getData().get("tool"));
    assertEquals(args, event.getData().get("arguments"));
  }

  /**
   * approval_required 事件应携带 replyId 供前端回填。
   */
  @Test
  @DisplayName("approvalRequired 工厂生成含 replyId 的审批事件")
  void approvalRequiredFactoryShouldContainReplyId() {
    SseEvent event = SseEvent.approvalRequired("req-1", "执行 SQL", "select * from t");

    assertEquals(SseEvent.EVENT_APPROVAL_REQUIRED, event.getEvent());
    assertEquals("req-1", event.getData().get("replyId"));
    assertEquals("req-1", event.getData().get("approvalId"));
    assertEquals("执行 SQL", event.getData().get("stepDescription"));
    assertEquals("select * from t", event.getData().get("summary"));
  }

  /**
   * approval_resolved 事件应携带审批结果。
   */
  @Test
  @DisplayName("approvalResolved 工厂生成审批完成事件")
  void approvalResolvedFactoryShouldContainStatus() {
    SseEvent event = SseEvent.approvalResolved("req-1", true, "admin", "同意");

    assertEquals(SseEvent.EVENT_APPROVAL_RESOLVED, event.getEvent());
    assertEquals("req-1", event.getData().get("replyId"));
    assertEquals(true, event.getData().get("approved"));
    assertEquals("admin", event.getData().get("approver"));
    assertEquals("同意", event.getData().get("comment"));
  }

  /**
   * withSource 应返回携带来源标识的新事件，不修改原事件。
   */
  @Test
  @DisplayName("withSource 返回新实例且不修改原事件")
  void withSourceShouldReturnNewEventWithoutMutatingOriginal() {
    SseEvent original = SseEvent.message("chunk");
    SseEvent sourced = original.withSource(SseEvent.SOURCE_MAIN);

    assertNotSame(original, sourced);
    assertNull(original.getSource());
    assertEquals(SseEvent.SOURCE_MAIN, sourced.getSource());
    assertTrue(sourced.hasSource());
  }

  /**
   * toPayload 应在 data 基础上追加 source 字段。
   */
  @Test
  @DisplayName("toPayload 包含 source 字段")
  void toPayloadShouldIncludeSource() {
    SseEvent event = SseEvent.message("chunk").withSource("supervisor/2");
    Map<String, Object> payload = event.toPayload();

    assertEquals("chunk", payload.get("content"));
    assertEquals("supervisor/2", payload.get(SseEvent.FIELD_SOURCE));
  }

  /**
   * 无 source 时，toPayload 不应包含 source 键。
   */
  @Test
  @DisplayName("toPayload 无 source 时不含 source 键")
  void toPayloadShouldNotIncludeSourceWhenAbsent() {
    SseEvent event = SseEvent.message("chunk");
    Map<String, Object> payload = event.toPayload();

    assertEquals("chunk", payload.get("content"));
    assertFalse(payload.containsKey(SseEvent.FIELD_SOURCE));
  }

  /**
   * toPayload 返回的映射应可安全修改，不影响不可变事件。
   */
  @Test
  @DisplayName("toPayload 返回可变的独立副本")
  void toPayloadShouldReturnMutableIndependentCopy() {
    SseEvent event = SseEvent.message("chunk").withSource(SseEvent.SOURCE_MAIN);
    Map<String, Object> payload = event.toPayload();
    payload.put("extra", "value");

    assertFalse(event.getData().containsKey("extra"));
  }
}
