package com.njydsz.cronjob.server.config;

import lombok.Data;

/**
 * HTTP 任务配置（P1-5）。
 *
 * <p>为 {@code jobType=HTTP} 的任务提供默认 HTTP 客户端参数。 任务级可在 paramsJson 中通过 {@code timeoutMs} 覆盖超时时间。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class HttpConfig {

  /** 默认connectTimeoutSeconds值（可被配置文件覆盖） */
  private static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 10;

  /** 默认requestTimeoutSeconds值（可被配置文件覆盖） */
  private static final int DEFAULT_REQUEST_TIMEOUT_SECONDS = 30;

  /** 默认连接超时（秒） */
  private int connectTimeoutSeconds = DEFAULT_CONNECT_TIMEOUT_SECONDS;

  /** 默认请求超时（秒），任务级可通过 paramsJson.timeoutMs 覆盖 */
  private int requestTimeoutSeconds = DEFAULT_REQUEST_TIMEOUT_SECONDS;

  /** 默认成功状态码范围（inclusive），如 "200-299" */
  private String successStatusRange = "200-299";

  /** 是否跟随重定向 */
  private boolean followRedirects = true;
}
