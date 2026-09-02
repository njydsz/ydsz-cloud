package com.njydsz.cronjob.server.core.cluster;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import com.njydsz.common.json.tree.ObjectNode;
import com.njydsz.common.json.tree.TextNode;
import com.njydsz.cronjob.server.config.CronjobProperties;
import com.njydsz.cronjob.server.config.MultiClusterConfig.RemoteCluster;

/**
 * 集群漂移 HTTP 客户端（P2-5）。
 *
 * <p>负责与远程集群通信：注册任务、注销任务、健康检查。
 * 每个目标集群复用独立的 {@link HttpClient} 实例（连接池共享）。
 *
 * <h3>线程安全</h3>
 *
 * <p>{@link HttpClient} 线程安全，{@code clients} Map 使用 {@link ConcurrentHashMap} 保证并发安全。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "ydsz.cronjob.multi-cluster", name = "enabled", havingValue = "true")
public class ClusterMigrationClient {

  /** HTTP 成功状态码 */
  private static final int HTTP_OK = 200;

  /** 集群漂移注册接口路径 */
  private static final String MIGRATE_REGISTER_PATH = "/api/v1/cronjob/internal/migrate/register";

  /** 集群漂移注销接口路径 */
  private static final String MIGRATE_UNREGISTER_PATH =
      "/api/v1/cronjob/internal/migrate/unregister";

  /** 健康检查接口路径 */
  private static final String HEALTH_PATH = "/actuator/health";

  private final CronjobProperties cronjobProperties;

  /** 复用的 HttpClient 实例映射（key = 集群名称） */
  private final ConcurrentHashMap<String, HttpClient> clients = new ConcurrentHashMap<>();

  public ClusterMigrationClient(CronjobProperties cronjobProperties) {
    this.cronjobProperties = cronjobProperties;
  }

  /**
   * 校验目标集群是否可达。
   *
   * @param clusterName 集群名称
   * @return true 表示可达
   */
  public boolean checkReachable(String clusterName) {
    RemoteCluster remote = getRemoteCluster(clusterName);
    if (remote == null) {
      return false;
    }
    try {
      HttpClient client = getOrCreateClient(clusterName, remote);
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(remote.getBaseUrl() + HEALTH_PATH))
              .timeout(Duration.ofSeconds(remote.getConnectTimeoutSeconds()))
              .GET()
              .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      return response.statusCode() == HTTP_OK;
    } catch (Exception e) {
      log.warn(
          "[ClusterMigration] 目标集群不可达: cluster={} reason={}", clusterName, e.getMessage());
      return false;
    }
  }

  /**
   * 调用远程集群注册任务。
   *
   * @param clusterName 目标集群名称
   * @param requestBody 注册请求 JSON
   * @return true 表示注册成功
   */
  public boolean registerJob(String clusterName, String requestBody) {
    RemoteCluster remote = getRemoteCluster(clusterName);
    if (remote == null) {
      return false;
    }
    try {
      HttpClient client = getOrCreateClient(clusterName, remote);
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(remote.getBaseUrl() + MIGRATE_REGISTER_PATH))
              .timeout(Duration.ofSeconds(remote.getRequestTimeoutSeconds()))
              .header("Content-Type", "application/json")
              .header("X-Ydsz-Internal-Token", remote.getAccessToken())
              .POST(HttpRequest.BodyPublishers.ofString(requestBody))
              .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == HTTP_OK) {
        return true;
      }
      log.warn(
          "[ClusterMigration] 远程注册失败: cluster={} status={} body={}",
          clusterName,
          response.statusCode(),
          response.body());
      return false;
    } catch (Exception e) {
      log.warn(
          "[ClusterMigration] 远程注册异常: cluster={} reason={}", clusterName, e.getMessage());
      return false;
    }
  }

  /**
   * 调用远程集群注销任务。
   *
   * @param clusterName 源集群名称（用于审计）
   * @param jobKey 任务 KEY
   * @return true 表示注销成功
   */
  public boolean unregisterJob(String clusterName, String jobKey) {
    RemoteCluster remote = getRemoteCluster(clusterName);
    if (remote == null) {
      return false;
    }
    try {
      HttpClient client = getOrCreateClient(clusterName, remote);
      ObjectNode body = new ObjectNode();
      body.put("jobKey", new TextNode(jobKey));
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(remote.getBaseUrl() + MIGRATE_UNREGISTER_PATH))
              .timeout(Duration.ofSeconds(remote.getRequestTimeoutSeconds()))
              .header("Content-Type", "application/json")
              .header("X-Ydsz-Internal-Token", remote.getAccessToken())
              .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
              .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      return response.statusCode() == HTTP_OK;
    } catch (Exception e) {
      log.warn(
          "[ClusterMigration] 远程注销异常: cluster={} jobKey={} reason={}",
          clusterName,
          jobKey,
          e.getMessage());
      return false;
    }
  }

  private RemoteCluster getRemoteCluster(String clusterName) {
    if (cronjobProperties.getMultiCluster() == null
        || cronjobProperties.getMultiCluster().getClusters() == null) {
      return null;
    }
    return cronjobProperties.getMultiCluster().getClusters().get(clusterName);
  }

  private HttpClient getOrCreateClient(String clusterName, RemoteCluster remote) {
    return clients.computeIfAbsent(
        clusterName,
        k ->
            HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(remote.getConnectTimeoutSeconds()))
                .build());
  }
}
