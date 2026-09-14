package com.njydsz.agent.server.middleware;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.njydsz.agent.domain.config.AgentProperties;
import com.njydsz.agent.domain.middleware.AgentMiddleware;
import com.njydsz.agent.domain.middleware.MiddlewareContext;

/**
 * {@link ToolResultEvictionMiddleware} 单元测试。
 *
 * @author ydsz-team
 * @since 26.09.14
 */
class ToolResultEvictionMiddlewareTest {

  /** 截断标记 */
  private static final String TRUNCATED = "...[truncated]";

  @Mock
  private AgentMiddleware.ActingProceed proceed;

  private MiddlewareContext context;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    context = new MiddlewareContext(null, "conv");
  }

  /**
   * 未启用时直接透传原始结果。
   */
  @Test
  @DisplayName("未启用时透传结果")
  void disabledShouldPassThrough() throws Exception {
    AgentProperties properties = new AgentProperties();
    properties.getTool().setEvictionEnabled(false);
    ToolResultEvictionMiddleware middleware = new ToolResultEvictionMiddleware(properties);
    Map<String, String> original = Map.of("r1", "a".repeat(10_000));
    when(proceed.execute()).thenReturn(original);

    middleware.onActing(context, proceed);

    assertEquals(original, context.getToolResults());
    verify(proceed).execute();
  }

  /**
   * 单结果字符超限时按配置截断。
   */
  @Test
  @DisplayName("单结果字符超限截断")
  void perResultCharLimitShouldTruncate() throws Exception {
    AgentProperties properties = new AgentProperties();
    properties.getTool().setEvictionEnabled(true);
    properties.getTool().setEvictionMaxResultChars(100);
    ToolResultEvictionMiddleware middleware = new ToolResultEvictionMiddleware(properties);
    Map<String, String> results = new HashMap<>();
    results.put("r1", "a".repeat(200));
    results.put("r2", "short");
    when(proceed.execute()).thenReturn(results);

    middleware.onActing(context, proceed);

    Map<String, String> evicted = context.getToolResults();
    assertEquals(100, evicted.get("r1").length());
    assertTrue(evicted.get("r1").endsWith(TRUNCATED));
    assertEquals("short", evicted.get("r2"));
  }

  /**
   * 单结果 Token 超限时按估算字符数截断。
   */
  @Test
  @DisplayName("单结果 Token 超限截断")
  void perResultTokenLimitShouldTruncate() throws Exception {
    AgentProperties properties = new AgentProperties();
    properties.getTool().setEvictionEnabled(true);
    properties.getTool().setEvictionMaxResultTokens(20);
    ToolResultEvictionMiddleware middleware = new ToolResultEvictionMiddleware(properties);
    when(proceed.execute()).thenReturn(Map.of("r1", "a".repeat(100)));

    middleware.onActing(context, proceed);

    String evicted = context.getToolResults().get("r1");
    // 20 Token * 2.5 = 50 字符上限
    assertTrue(evicted.endsWith(TRUNCATED));
    assertTrue(evicted.length() <= 50 + TRUNCATED.length());
  }

  /**
   * 总字符超限时按比例压缩各结果。
   */
  @Test
  @DisplayName("总字符超限按比例压缩")
  void totalCharLimitShouldCompressProportionally() throws Exception {
    AgentProperties properties = new AgentProperties();
    properties.getTool().setEvictionEnabled(true);
    properties.getTool().setEvictionMaxResultChars(-1);
    properties.getTool().setEvictionMaxTotalChars(100);
    ToolResultEvictionMiddleware middleware = new ToolResultEvictionMiddleware(properties);
    Map<String, String> results = new HashMap<>();
    results.put("r1", "a".repeat(300));
    results.put("r2", "b".repeat(100));
    when(proceed.execute()).thenReturn(results);

    middleware.onActing(context, proceed);

    Map<String, String> evicted = context.getToolResults();
    int total = evicted.values().stream().mapToInt(String::length).sum();
    assertTrue(total <= 100 + TRUNCATED.length() * 2, "总字符应接近上限");
    assertTrue(evicted.get("r1").endsWith(TRUNCATED));
    assertTrue(evicted.get("r2").endsWith(TRUNCATED));
    assertTrue(evicted.get("r1").length() > evicted.get("r2").length(), "长结果应被压缩更多");
  }

  /**
   * 结果为空或 null 时应优雅处理。
   */
  @Test
  @DisplayName("空结果不报错")
  void emptyResultsShouldHandleGracefully() throws Exception {
    AgentProperties properties = new AgentProperties();
    properties.getTool().setEvictionEnabled(true);
    ToolResultEvictionMiddleware middleware = new ToolResultEvictionMiddleware(properties);
    when(proceed.execute()).thenReturn(Map.of());

    middleware.onActing(context, proceed);

    assertTrue(context.getToolResults().isEmpty());
  }

  /**
   * 优先级应为 TOOL_EVICTION_PRIORITY。
   */
  @Test
  @DisplayName("优先级为 TOOL_EVICTION_PRIORITY")
  void priorityShouldBeToolEviction() {
    ToolResultEvictionMiddleware middleware =
        new ToolResultEvictionMiddleware(new AgentProperties());

    assertEquals(AgentMiddleware.TOOL_EVICTION_PRIORITY, middleware.getPriority());
    assertEquals("tool-result-eviction", middleware.getName());
  }
}
