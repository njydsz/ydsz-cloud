package com.remisoft.cronjob.server.core.dispatch;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.remisoft.common.json.RemiJson;
import com.remisoft.common.json.tree.ObjectNode;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 跨集群调度器（P3-12 跨集群调度）。
 *
 * <p>支持将任务派发到其他集群的执行器节点，实现多集群统一调度：
 * <ul>
 *   <li>通过配置 {@code remi.cronjob.clusters} 定义多个集群端点</li>
 *   <li>任务的 {@code cluster} 字段指定目标集群（null=本地集群）</li>
 *   <li>跨集群派发通过 HTTP API 调用目标集群的 /cronjob/internal/execute 接口</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class CrossClusterDispatcher {

    /** 集群端点缓存: clusterName -> baseUrl */
    private final Map<String, String> clusterEndpoints = new ConcurrentHashMap<>();

    /** 复用的 HttpClient */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * 初始化集群端点配置。
     *
     * @param clusters 集群配置: clusterName -> baseUrl
     */
    public void initClusters(Map<String, String> clusters) {
        clusterEndpoints.clear();
        clusterEndpoints.putAll(clusters);
        log.info("[CrossCluster] 初始化集群端点: count={} clusters={}",
                clusters.size(), clusters.keySet());
    }

    /**
     * 跨集群派发任务。
     *
     * @param clusterName 目标集群名称
     * @param request     远程派发请求
     * @return 执行日志 ID；派发失败返回 null
     */
    public String dispatchToCluster(String clusterName, RemoteTaskRequest request) {
        String baseUrl = clusterEndpoints.get(clusterName);
        if (baseUrl == null) {
            log.warn("[CrossCluster] 集群端点未配置: cluster={}", clusterName);
            return null;
        }
        String url = baseUrl + "/cronjob/internal/execute";
        String requestBody = RemiJson.toJson(request);
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("X-Cluster-Source", "remi")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                ObjectNode json = RemiJson.parseObject(response.body());
                int code = json.getIntegerOrDefault("code", -1);
                if (code == 0) {
                    String logId = json.getString("data");
                    log.info("[CrossCluster] 跨集群派发成功: cluster={} key={} logId={}",
                            clusterName, request.getJob().getJobKey(), logId);
                    return logId;
                }
            }
            log.warn("[CrossCluster] 跨集群派发失败: cluster={} status={} body={}",
                    clusterName, response.statusCode(),
                    response.body() == null ? "" : response.body().substring(0, Math.min(200, response.body().length())));
            return null;
        } catch (Exception e) {
            log.error("[CrossCluster] 跨集群派发异常: cluster={} reason={}", clusterName, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 判断集群是否已配置。
     */
    public boolean isClusterAvailable(String clusterName) {
        return clusterEndpoints.containsKey(clusterName);
    }
}
