package com.njydsz.cronjob.server.core.handler;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Configuration;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.json.tree.JsonNode;
import com.njydsz.common.json.tree.ObjectNode;
import com.njydsz.cronjob.domain.job.JobExecutionException;
import com.njydsz.cronjob.domain.job.JobHandler;
import com.njydsz.cronjob.server.config.CronjobProperties;
import com.njydsz.cronjob.server.config.HttpConfig;

/**
 * HTTP 任务处理器（P1-5）。
 *
 * <p>支持 {@code jobType=HTTP} 的任务，通过 HTTP 调用外部 API 执行业务逻辑。
 * 任务处理。
 *
 * <h3>paramsJson 格式</h3>
 *
 * <pre>{@code
 * {
 *   "url": "https://api.example.com/endpoint",
 *   "method": "POST",
 *   "headers": {
 *     "Content-Type": "application/json",
 *     "Authorization": "Bearer xxx"
 *   },
 *   "body": "{\"key\":\"value\"}",
 *   "timeoutMs": 30000,
 *   "successStatus": "200-299"
 * }
 * }</pre>
 *
 * <h3>字段说明</h3>
 *
 * <ul>
 *   <li>{@code url}（必填）: 目标 URL
 *   <li>{@code method}（可选）: HTTP 方法，默认 GET
 *   <li>{@code headers}（可选）: 请求头键值对
 *   <li>{@code body}（可选）: 请求体（POST/PUT/PATCH 时使用）
 *   <li>{@code timeoutMs}（可选）: 请求超时毫秒，覆盖全局默认值
 *   <li>{@code successStatus}（可选）: 成功状态码范围，如 "200-299" 或 "200,201,204"
 * </ul>
 *
 * <p>使用 JDK 内置 {@link HttpClient}，避免引入第三方 HTTP 客户端依赖。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@ConditionalOnMissingBean(HttpJobHandler.class)
public class HttpJobHandler implements JobHandler {
  /** HTTP 成功状态码下限 */
  private static final int HTTP_OK_MIN = 200;

  /** HTTP 成功状态码上限（不含） */
  private static final int HTTP_OK_MAX_EXCLUSIVE = 300;

  /** 响应体日志截断长度 */
  private static final int BODY_LOG_MAX_LENGTH = 500;


  /** Bean 名称，dispatcher 在 jobType=HTTP 时路由到此 handler */
  public static final String BEAN_NAME = "httpJobHandler";

  // Schema validation removed — using inline checks in validateParams

  private final CronjobProperties cronjobProperties;
  private final HttpClient httpClient;

  public HttpJobHandler(CronjobProperties cronjobProperties) {
    this.cronjobProperties = cronjobProperties;
    HttpConfig httpConfig = cronjobProperties.getHttp();
    this.httpClient =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(httpConfig.getConnectTimeoutSeconds()))
            .followRedirects(
                httpConfig.isFollowRedirects()
                    ? HttpClient.Redirect.NORMAL
                    : HttpClient.Redirect.NEVER)
            .build();
  }

  @Override
  public Object execute(String paramsJson) throws JobExecutionException {
    if (paramsJson == null || paramsJson.isBlank()) {
      throw new IllegalArgumentException("HTTP 任务参数(paramsJson)为空");
    }

    ObjectNode params = YdszJson.parseObject(paramsJson);

    String url = params.getString("url");
    if (url == null || url.isBlank()) {
      throw new IllegalArgumentException("HTTP 任务参数缺少 url");
    }

    String method = params.getString("method");
    if (method == null || method.isBlank()) {
      method = "GET";
    }
    method = method.toUpperCase();

    String body = params.getString("body");
    Integer timeoutMs = params.getInteger("timeoutMs");
    String successStatus = params.getString("successStatus");
    if (successStatus == null || successStatus.isBlank()) {
      successStatus = cronjobProperties.getHttp().getSuccessStatusRange();
    }

    // 构建请求
    HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().uri(URI.create(url));

    // 设置超时
    Duration timeout =
        timeoutMs != null && timeoutMs > 0
            ? Duration.ofMillis(timeoutMs)
            : Duration.ofSeconds(cronjobProperties.getHttp().getRequestTimeoutSeconds());
    requestBuilder.timeout(timeout);

    // 设置请求头
    ObjectNode headers = params.getObjectNode("headers");
    if (headers != null) {
      for (Map.Entry<String, JsonNode> entry : headers.entrySet()) {
        if (entry.getValue() != null && !entry.getValue().isNull()) {
          requestBuilder.header(entry.getKey(), entry.getValue().asText());
        }
      }
    }

    // 设置 HTTP 方法和请求体
    HttpRequest.BodyPublisher bodyPublisher =
        body != null && !body.isBlank()
            ? HttpRequest.BodyPublishers.ofString(body)
            : HttpRequest.BodyPublishers.noBody();
    switch (method) {
      case "GET" -> requestBuilder.GET();
      case "POST" -> requestBuilder.POST(bodyPublisher);
      case "PUT" -> requestBuilder.PUT(bodyPublisher);
      case "PATCH" -> requestBuilder.method("PATCH", bodyPublisher);
      case "DELETE" -> requestBuilder.DELETE();
      case "HEAD" -> requestBuilder.method("HEAD", HttpRequest.BodyPublishers.noBody());
      default -> throw new IllegalArgumentException("不支持的 HTTP 方法: " + method);
    }

    // 执行请求（IO/中断异常统一转为 JobExecutionException，保持执行失败语义）
    log.info(
        "[HttpJobHandler] 发送请求: method={} url={} timeoutMs={}", method, url, timeout.toMillis());
    HttpResponse<String> response;
    try {
      response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      throw new JobExecutionException("HTTP 请求失败: " + url + " reason=" + e.getMessage(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new JobExecutionException("HTTP 请求被中断: " + url + " reason=" + e.getMessage(), e);
    }

    int status = response.statusCode();
    String responseBody = response.body();

    // 校验响应状态码
    if (!isSuccessStatus(status, successStatus)) {
      throw new IllegalStateException(
          "HTTP 请求失败: status=" + status + " url=" + url + " body=" + truncate(responseBody));
    }

    log.info(
        "[HttpJobHandler] 请求成功: method={} url={} status={} bodyLen={}",
        method,
        url,
        status,
        responseBody == null ? 0 : responseBody.length());

    // 返回结构化结果
    ObjectNode result = new ObjectNode();
    result.put("status", status);
    result.put("body", responseBody);
    result.put("url", url);
    result.put("method", method);
    return result;
  }

  /**
   * 判断 HTTP 状态码是否在成功范围内。
   *
   * <p>支持两种格式：
   *
   * <ul>
   *   <li>范围格式: "200-299"
   *   <li>列表格式: "200,201,204"
   * </ul>
   */
  private boolean isSuccessStatus(int status, String successStatus) {
    if (successStatus == null || successStatus.isBlank()) {
      return status >= HTTP_OK_MIN && status < HTTP_OK_MAX_EXCLUSIVE;
    }
    String trimmed = successStatus.trim();
    if (trimmed.contains("-")) {
      String[] parts = trimmed.split("-");
      if (parts.length == 2) {
        try {
          int min = Integer.parseInt(parts[0].trim());
          int max = Integer.parseInt(parts[1].trim());
          return status >= min && status <= max;
        } catch (NumberFormatException e) {
          log.warn("[HttpJobHandler] 无效的成功状态码范围: {}", successStatus);
          return status >= HTTP_OK_MIN && status < HTTP_OK_MAX_EXCLUSIVE;
        }
      }
    }
    if (trimmed.contains(",")) {
      String[] codes = trimmed.split(",");
      for (String code : codes) {
        try {
          if (status == Integer.parseInt(code.trim())) {
            return true;
          }
        } catch (NumberFormatException e) {
          // skip invalid code
        }
      }
      return false;
    }
    try {
      return status == Integer.parseInt(trimmed);
    } catch (NumberFormatException e) {
      return status >= HTTP_OK_MIN && status < HTTP_OK_MAX_EXCLUSIVE;
    }
  }

  /** 截断字符串，避免日志过长。 */
  private String truncate(String s) {
    if (s == null) {
      return "";
    }
    return s.length() > BODY_LOG_MAX_LENGTH ? s.substring(0, BODY_LOG_MAX_LENGTH) + "..." : s;
  }
}
