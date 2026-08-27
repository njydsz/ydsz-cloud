package com.njydsz.cronjob.cli.command;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * HTTP 命令基类（P2-7）。
 *
 * <p>封装 Java 11 HttpClient，提供统一的 GET/POST 请求能力。子类只需关注业务逻辑。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public abstract class AbstractHttpCommand implements CliCommand {

  /** HTTP 请求超时时间 */
  private static final Duration TIMEOUT = Duration.ofSeconds(10);

  /** 服务端地址 */
  protected final String serverUrl;

  /** HTTP 客户端（线程安全，可复用） */
  protected final HttpClient httpClient;

  /**
   * 构造命令。
   *
   * @param serverUrl 服务端地址
   */
  protected AbstractHttpCommand(String serverUrl) {
    this.serverUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
    this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
  }

  /**
   * 发送 GET 请求。
   *
   * @param path API 路径（含前导 /）
   * @return 响应体字符串
   * @throws Exception 请求异常
   */
  protected String get(String path) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder().uri(URI.create(serverUrl + path)).timeout(TIMEOUT).GET().build();
    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    return response.body();
  }

  /**
   * 发送 POST 请求。
   *
   * @param path API 路径（含前导 /）
   * @return 响应体字符串
   * @throws Exception 请求异常
   */
  protected String post(String path) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(serverUrl + path))
            .timeout(TIMEOUT)
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();
    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    return response.body();
  }
}
