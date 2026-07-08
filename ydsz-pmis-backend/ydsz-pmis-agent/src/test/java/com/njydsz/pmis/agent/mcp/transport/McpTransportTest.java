package com.njydsz.pmis.agent.mcp.transport;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MCP 传输层单元测试（P3-3 落地）。
 *
 * <p>覆盖构造参数校验和基本状态判断。
 * 真实连接测试由 {@link McpClientManagerTest} 通过 MockTransport 覆盖。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-3)
 */
@DisplayName("MCP 传输层")
class McpTransportTest {

    @Nested
    @DisplayName("StdioMcpTransport 构造校验")
    class StdioConstructorTest {

        @Test
        @DisplayName("command 为 null 抛异常")
        void shouldThrowWhenCommandNull() {
            assertThatThrownBy(() -> new StdioMcpTransport(null, null, null, 1000))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("command 为空列表抛异常")
        void shouldThrowWhenCommandEmpty() {
            assertThatThrownBy(() -> new StdioMcpTransport(List.of(), null, null, 1000))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("正常构造初始未连接")
        void shouldStartDisconnected() {
            StdioMcpTransport transport = new StdioMcpTransport(
                    List.of("echo", "test"), null, null, 1000);

            assertThat(transport.isConnected()).isFalse();
        }

        @Test
        @DisplayName("close 后未连接")
        void shouldBeDisconnectedAfterClose() {
            StdioMcpTransport transport = new StdioMcpTransport(
                    List.of("echo"), null, null, 1000);

            transport.close();

            assertThat(transport.isConnected()).isFalse();
        }

        @Test
        @DisplayName("connect 前调用 send 抛异常")
        void shouldThrowWhenSendBeforeConnect() {
            StdioMcpTransport transport = new StdioMcpTransport(
                    List.of("echo"), null, null, 1000);

            assertThatThrownBy(() -> transport.send("{}"))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("connect 前调用 receive 抛异常")
        void shouldThrowWhenReceiveBeforeConnect() {
            StdioMcpTransport transport = new StdioMcpTransport(
                    List.of("echo"), null, null, 1000);

            assertThatThrownBy(() -> transport.receive())
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("HttpMcpTransport 构造校验")
    class HttpConstructorTest {

        @Test
        @DisplayName("url 为 null 抛异常")
        void shouldThrowWhenUrlNull() {
            assertThatThrownBy(() -> new HttpMcpTransport(null, 1000))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("url 为空字符串抛异常")
        void shouldThrowWhenUrlBlank() {
            assertThatThrownBy(() -> new HttpMcpTransport("  ", 1000))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("正常构造初始未连接")
        void shouldStartDisconnected() {
            HttpMcpTransport transport = new HttpMcpTransport("http://localhost:8080", 1000);

            assertThat(transport.isConnected()).isFalse();
        }

        @Test
        @DisplayName("connect 后变为已连接")
        void shouldConnectSuccessfully() throws Exception {
            HttpMcpTransport transport = new HttpMcpTransport("http://localhost:8080", 1000);

            transport.connect();

            assertThat(transport.isConnected()).isTrue();
        }

        @Test
        @DisplayName("connect 前调用 send 抛异常")
        void shouldThrowWhenSendBeforeConnect() {
            HttpMcpTransport transport = new HttpMcpTransport("http://localhost:8080", 1000);

            assertThatThrownBy(() -> transport.send("{}"))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("receive 前未 send 抛异常")
        void shouldThrowWhenReceiveBeforeSend() throws Exception {
            HttpMcpTransport transport = new HttpMcpTransport("http://localhost:8080", 1000);
            transport.connect();

            assertThatThrownBy(() -> transport.receive())
                    .isInstanceOf(java.io.IOException.class)
                    .hasMessageContaining("没有待接收的响应");
        }

        @Test
        @DisplayName("close 后变为未连接")
        void shouldBeDisconnectedAfterClose() throws Exception {
            HttpMcpTransport transport = new HttpMcpTransport("http://localhost:8080", 1000);
            transport.connect();
            transport.close();

            assertThat(transport.isConnected()).isFalse();
        }

        @Test
        @DisplayName("重复 connect 不抛异常")
        void shouldNotThrowOnDoubleConnect() throws Exception {
            HttpMcpTransport transport = new HttpMcpTransport("http://localhost:8080", 1000);

            transport.connect();
            transport.connect();

            assertThat(transport.isConnected()).isTrue();
        }
    }
}
