package com.njydsz.agent.infra.mcp;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link McpJsonRpcMessage} 协议模型测试（26.11.06 下沉至 domain 后的兼容别名）。
 *
 * <p><b>背景</b>：协议契约已下沉至 {@code ydzs-agent-domain}，本 record 保留为
 * {@code @Deprecated} 别名以兼容存量引用。本测试确保别名与 domain 版本行为一致，
 * 避免迁移期间出现语义漂移。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
class McpJsonRpcMessageTest {

  @Test
  @DisplayName("success：构造成功响应，isResponse=true")
  void shouldBuildSuccessResponse() {
    McpJsonRpcMessage message = McpJsonRpcMessage.success("req-1", Map.of("ok", true));

    assertThat(message.jsonrpc()).isEqualTo("2.0");
    assertThat(message.id()).isEqualTo("req-1");
    assertThat(message.result()).isNotNull();
    assertThat(message.error()).isNull();
    assertThat(message.isResponse()).isTrue();
    assertThat(message.isRequest()).isFalse();
    assertThat(message.isNotification()).isFalse();
  }

  @Test
  @DisplayName("error：构造错误响应，含 code/message")
  void shouldBuildErrorResponse() {
    McpJsonRpcMessage message =
        McpJsonRpcMessage.error("req-2", McpJsonRpcMessage.ERROR_METHOD_NOT_FOUND, "no method");

    assertThat(message.id()).isEqualTo("req-2");
    assertThat(message.error()).containsEntry("code", McpJsonRpcMessage.ERROR_METHOD_NOT_FOUND);
    assertThat(message.error()).containsEntry("message", "no method");
    assertThat(message.result()).isNull();
    assertThat(message.isResponse()).isTrue();
  }

  @Test
  @DisplayName("notification：无 id，isNotification=true")
  void shouldBuildNotification() {
    McpJsonRpcMessage message =
        McpJsonRpcMessage.notification("tools/list", Map.of("cursor", "abc"));

    assertThat(message.id()).isNull();
    assertThat(message.method()).isEqualTo("tools/list");
    assertThat(message.params()).containsEntry("cursor", "abc");
    assertThat(message.isNotification()).isTrue();
    assertThat(message.isRequest()).isFalse();
    assertThat(message.isResponse()).isFalse();
  }

  @Test
  @DisplayName("错误码常量与 domain 版本一致")
  void shouldKeepErrorCodesAlignedWithDomain() {
    assertThat(McpJsonRpcMessage.ERROR_PARSE_ERROR).isEqualTo(-32700);
    assertThat(McpJsonRpcMessage.ERROR_INVALID_REQUEST).isEqualTo(-32600);
    assertThat(McpJsonRpcMessage.ERROR_METHOD_NOT_FOUND).isEqualTo(-32601);
    assertThat(McpJsonRpcMessage.ERROR_INVALID_PARAMS).isEqualTo(-32602);
    assertThat(McpJsonRpcMessage.ERROR_INTERNAL).isEqualTo(-32603);
    assertThat(McpJsonRpcMessage.JSONRPC_VERSION)
        .isEqualTo(com.njydsz.agent.domain.mcp.McpJsonRpcMessage.JSONRPC_VERSION);
  }

  @Test
  @DisplayName("equals/hashCode：record 语义正确")
  void shouldImplementRecordEquality() {
    McpJsonRpcMessage a = McpJsonRpcMessage.success("1", "r");
    McpJsonRpcMessage b = McpJsonRpcMessage.success("1", "r");

    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    assertThat(a).isNotEqualTo(McpJsonRpcMessage.success("2", "r"));
  }

  @Test
  @DisplayName("参数列表可为空集合")
  void shouldAcceptEmptyParams() {
    McpJsonRpcMessage message = McpJsonRpcMessage.notification("ping", Map.of());

    assertThat(message.params()).isEmpty();
    assertThat(message.isNotification()).isTrue();
  }

  @Test
  @DisplayName("toString 包含协议版本与方法名")
  void shouldIncludeVersionInToString() {
    McpJsonRpcMessage message = McpJsonRpcMessage.notification("ping", Map.of());

    assertThat(message.toString()).contains("2.0").contains("ping");
  }

  @Test
  @DisplayName("嵌套 Map 参数保持结构")
  void shouldKeepNestedParams() {
    Map<String, Object> nested = Map.of("tools", List.of(Map.of("name", "t1")));
    McpJsonRpcMessage message = McpJsonRpcMessage.notification("tools/list", nested);

    assertThat(message.params()).containsKey("tools");
  }
}
