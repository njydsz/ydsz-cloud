package com.njydsz.pmis.agent.mcp.transport;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Stdio 传输实现（P3-3 落地）。
 *
 * <p>通过 {@link ProcessBuilder} 启动子进程，经 stdin/stdout 以行分隔 JSON-RPC 消息。
 * 适用于本地 MCP 服务端（如 npx @modelcontextprotocol/server-filesystem）。
 *
 * <p>消息格式：每行一个 JSON-RPC 消息（以 \n 分隔）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-3)
 */
@Slf4j
public class StdioMcpTransport implements McpTransport {

    private final List<String> command;
    private final Map<String, String> env;
    private final String workingDir;
    private final long readTimeoutMs;

    private Process process;
    private BufferedWriter writer;
    private BufferedReader reader;
    private final AtomicBoolean connected = new AtomicBoolean(false);

    /**
     * 构造 stdio 传输。
     *
     * @param command       子进程命令（如 ["npx", "@modelcontextprotocol/server-filesystem", "/tmp"]）
     * @param env           环境变量（可为 null）
     * @param workingDir    工作目录（可为 null）
     * @param readTimeoutMs 读取超时毫秒
     */
    public StdioMcpTransport(List<String> command, Map<String, String> env,
                             String workingDir, long readTimeoutMs) {
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("command 不能为空");
        }
        this.command = command;
        this.env = env;
        this.workingDir = workingDir;
        this.readTimeoutMs = readTimeoutMs;
    }

    @Override
    public void connect() throws Exception {
        if (connected.get()) {
            return;
        }
        ProcessBuilder pb = new ProcessBuilder(command);
        if (env != null) {
            pb.environment().putAll(env);
        }
        if (workingDir != null) {
            pb.directory(new java.io.File(workingDir));
        }
        pb.redirectErrorStream(false);
        process = pb.start();
        writer = new BufferedWriter(new OutputStreamWriter(
                process.getOutputStream(), StandardCharsets.UTF_8));
        reader = new BufferedReader(new InputStreamReader(
                process.getInputStream(), StandardCharsets.UTF_8));
        connected.set(true);
        log.info("[MCP-Stdio] 子进程已启动: {}", command);
    }

    @Override
    public void send(String json) throws Exception {
        ensureConnected();
        writer.write(json);
        writer.newLine();
        writer.flush();
    }

    @Override
    public String receive() throws Exception {
        ensureConnected();
        if (readTimeoutMs > 0) {
            // BufferedReader.readLine 阻塞，通过 daemon 线程模拟超时
            String[] result = new String[1];
            Exception[] err = new Exception[1];
            Thread t = new Thread(() -> {
                try {
                    result[0] = reader.readLine();
                } catch (Exception e) {
                    err[0] = e;
                }
            }, "mcp-stdio-read");
            t.setDaemon(true);
            t.start();
            t.join(readTimeoutMs);
            if (t.isAlive()) {
                t.interrupt();
                throw new java.io.IOException("读取 MCP 响应超时 (" + readTimeoutMs + "ms)");
            }
            if (err[0] != null) {
                throw err[0];
            }
            if (result[0] == null) {
                throw new java.io.IOException("MCP 服务端关闭连接");
            }
            return result[0];
        }
        String line = reader.readLine();
        if (line == null) {
            throw new java.io.IOException("MCP 服务端关闭连接");
        }
        return line;
    }

    @Override
    public boolean isConnected() {
        return connected.get() && process != null && process.isAlive();
    }

    @Override
    public void close() {
        connected.set(false);
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                log.debug("[MCP-Stdio] 关闭 writer 失败: {}", e.getMessage());
            }
        }
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException e) {
                log.debug("[MCP-Stdio] 关闭 reader 失败: {}", e.getMessage());
            }
        }
        if (process != null && process.isAlive()) {
            process.destroy();
            try {
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    log.warn("[MCP-Stdio] 子进程未在 5s 内退出，已强制终止");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
        log.info("[MCP-Stdio] 连接已关闭");
    }

    private void ensureConnected() {
        if (!connected.get() || process == null) {
            throw new IllegalStateException("传输未连接，请先调用 connect()");
        }
    }
}
