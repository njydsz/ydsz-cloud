package com.njydsz.common.sentry.logging;

import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.locks.ReentrantLock;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.sentry.domain.LogEvent;
import com.njydsz.common.sentry.resilience.CircuitBreaker;
import com.njydsz.common.sentry.spi.LogPublisher;

/**
 * ELK + Logstash 日志发布器
 *
 * <p>通过 TCP/UDP 将结构化 JSON 日志推送到 Logstash。 Logstash 解析后写入 Elasticsearch，由 Kibana 展示。
 *
 * <p>TCP 模式下复用长连接，避免频繁 TCP 握手开销。 UDP 模式下复用 DatagramSocket（无连接，构造时创建）。 熔断保护由 {@link CircuitBreaker}
 * 统一管理。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class ElkLogPublisher implements LogPublisher, AutoCloseable {

  private final String host;
  private final int port;
  private final String protocol;
  private final int connectTimeoutMillis;
  private final int readTimeoutMillis;
  private final int maxRetryAttempts;
  private final CircuitBreaker circuitBreaker;

  /** TCP 长连接（仅在 TCP 模式下使用） */
  private volatile Socket tcpSocket;

  private final ReentrantLock tcpLock = new ReentrantLock();

  /** UDP socket 复用（仅 UDP 模式） */
  private DatagramSocket udpSocket;

  public ElkLogPublisher(
      String host,
      int port,
      String protocol,
      int connectTimeoutMillis,
      int readTimeoutMillis,
      int maxRetryAttempts,
      CircuitBreaker circuitBreaker) {
    this.host = host;
    this.port = port;
    this.protocol = protocol != null ? protocol.toLowerCase() : "tcp";
    this.connectTimeoutMillis = connectTimeoutMillis;
    this.readTimeoutMillis = readTimeoutMillis;
    this.maxRetryAttempts = maxRetryAttempts;
    this.circuitBreaker = circuitBreaker;
    log.info("[Sentry] ElkLogPublisher 初始化: {}://{}:{}", this.protocol, host, port);
    if ("udp".equals(this.protocol)) {
      try {
        udpSocket = new DatagramSocket();
      } catch (Exception e) {
        log.warn("[Sentry] UDP socket 创建失败: {}", e.getMessage());
      }
    }
  }

  @Override
  /**
   * publish。
   * @param event 参数
   * @return 结果
   */
  public boolean publish(LogEvent event) {
    if (!isAvailable()) {
      return false;
    }
    String json = LogEventSerializer.toJson(event);
    byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

    return circuitBreaker.execute(
        () -> {
          for (int attempt = 1; attempt <= maxRetryAttempts; attempt++) {
            try {
              if ("udp".equals(protocol)) {
                sendUdp(bytes);
              } else {
                sendTcp(bytes);
              }
              return true;
            } catch (Exception e) {
              log.debug(
                  "[Sentry] ELK 日志发布失败 (attempt {}/{}): {}",
                  attempt,
                  maxRetryAttempts,
                  e.getMessage());
              if (attempt < maxRetryAttempts) {
                try {
                  Thread.sleep(100L * attempt);
                } catch (InterruptedException ie) {
                  Thread.currentThread().interrupt();
                  break;
                }
              }
            }
          }
          return false;
        },
        () -> false);
  }

  /** TCP 发送（长连接复用） */
  private void sendTcp(byte[] bytes) throws IOException {
    tcpLock.lock();
    try {
      Socket socket = ensureTcpConnection();
      try {
        OutputStream os = socket.getOutputStream();
        os.write(bytes);
        os.write('\n');
        os.flush();
      } catch (IOException e) {
        closeTcpSocketQuietly();
        throw e;
      }
    } finally {
      tcpLock.unlock();
    }
  }

  /** 确保 TCP 连接可用（懒创建 + 断线重连） */
  private Socket ensureTcpConnection() throws IOException {
    Socket socket = tcpSocket;
    if (socket != null && !socket.isClosed() && socket.isConnected()) {
      return socket;
    }
    // 关闭旧连接
    closeTcpSocketQuietly();
    // 创建新连接
    socket = new Socket();
    socket.connect(new InetSocketAddress(host, port), connectTimeoutMillis);
    socket.setSoTimeout(readTimeoutMillis);
    socket.setKeepAlive(true);
    socket.setTcpNoDelay(true);
    tcpSocket = socket;
    log.debug("[Sentry] ELK TCP 连接已建立: {}:{}", host, port);
    return socket;
  }

  private void closeTcpSocketQuietly() {
    Socket socket = tcpSocket;
    if (socket != null) {
      try {
        socket.close();
      } catch (Exception ignored) {
        // quiet close
      }
      tcpSocket = null;
    }
  }

  /** UDP 发送 */
  private void sendUdp(byte[] bytes) throws IOException {
    if (udpSocket == null) {
      udpSocket = new DatagramSocket();
    }
    DatagramPacket packet =
        new DatagramPacket(bytes, bytes.length, new InetSocketAddress(host, port));
    udpSocket.send(packet);
  }

  @Override
  /**
   * is available。
   * @return 结果
   */
  public boolean isAvailable() {
    return circuitBreaker == null || circuitBreaker.getState() != CircuitBreaker.State.OPEN;
  }

  @Override
  /**
   * get name。
   * @return 结果
   */
  public String getName() {
    return "elk-logstash";
  }

  @Override
  /**
   * get scheme。
   * @return 结果
   */
  public String getScheme() {
    return "elk";
  }

  /** 获取熔断器状态 */
  /**
   * get circuit breaker。
   * @return 结果
   */
  public CircuitBreaker.State getCircuitBreakerState() {
    return circuitBreaker != null ? circuitBreaker.getState() : CircuitBreaker.State.CLOSED;
  }

  @Override
  /**
   * close。
   */
  public void close() {
    closeTcpSocketQuietly();
    if (udpSocket != null) {
      try {
        udpSocket.close();
      } catch (Exception ignored) {
        log.debug("Caught exception (ignored): {}", ignored.getMessage());
      }
      udpSocket = null;
    }
    log.info("[Sentry] ElkLogPublisher 已关闭");
  }
}
