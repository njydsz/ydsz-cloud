package com.njydsz.cronjob.server.core.dispatch;

import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.json.object.JsonObject;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Configuration;

import com.njydsz.cronjob.domain.entity.job.JobNode;
import com.njydsz.cronjob.server.config.CronjobProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * 远程任务派发客户端（P1-4）。
 *
 * <p>Leader 节点通过本客户端将分片任务通过 HTTP POST 派发到选定的执行器节点。
 * 执行器节点收到请求后在本地执行，返回执行日志 ID。
 *
 * <h3>调用链路</h3>
 * <pre>
 * Leader.executeShardedJob
 *   └─ RemoteTaskClient.dispatch(node, request)
 *        └─ HTTP POST → http://{node.host}:{node.port}/cronjob/internal/execute
 *             └─ Executor.InternalJobController.execute(request)
 *                  └─ TaskDispatcher.executeLocally(job, triggerType, shardIndex, shardTotal)
 *                       └─ executeShard(...) → 返回 logId
 *        └─ 解析响应 JSON → 返回 logId（失败返回 null）
 * </pre>
 *
 * <h3>错误处理</h3>
 * <ul>
 *   <li>连接拒绝/超时：返回 null，调用方决定是否降级本地执行</li>
 *   <li>HTTP 5xx：返回 null，执行器端已记录 FAILED 日志</li>
 *   <li>HTTP 4xx：返回 null，记录参数错误日志</li>
 *   <li>响应解析失败：返回 null，记录警告</li>
 * </ul>
 *
 * <p>使用 JDK 内置 {@link HttpClient}，避免引入第三方 HTTP 客户端依赖。
 * HttpClient 实例复用（{@link #httpClient}），避免每次请求创建新连接池。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@ConditionalOnMissingBean(RemoteTaskClient.class)
public class RemoteTaskClient {

    /** 内部执行接口路径 */
    private static final String INTERNAL_EXECUTE_PATH = "/cronjob/internal/execute";
    /** P0-1: 子任务执行接口路径 */
    private static final String INTERNAL_SUB_TASK_PATH = "/cronjob/internal/execute-sub-task";

    private final CronjobProperties cronjobProperties;

    /** 复用的 HttpClient 实例（线程安全） */
    private final HttpClient httpClient;

    /**
     * 构造远程任务客户端。
     *
     * @param cronjobProperties 调度配置（读取 remote.* 参数）
     */
    public RemoteTaskClient(CronjobProperties cronjobProperties) {
        this.cronjobProperties = cronjobProperties;
        CronjobProperties.Remote remoteConfig = cronjobProperties.getRemote();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(remoteConfig.getConnectTimeoutSeconds()))
                .build();
    }

    /**
     * 派发任务到远程执行器节点。
     *
     * @param node    执行器节点（含 host 和 port）
     * @param request 远程派发请求（job + triggerType + shardIndex + shardTotal + traceId）
     * @return 执行日志 ID；派发失败返回 null
     */
    public String dispatch(JobNode node, RemoteTaskRequest request) {
        if (node == null || node.getHost() == null || node.getPort() == null) {
            log.warn("[RemoteClient] 节点地址不完整, 跳过远程派发: nodeId={}", 
                    node == null ? "null" : node.getNodeId());
            return null;
        }
        String url = buildUrl(node.getHost(), node.getPort());
        String requestBody = YdszJson.toJson(request);
        CronjobProperties.Remote remoteConfig = cronjobProperties.getRemote();

        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(remoteConfig.getRequestTimeoutSeconds()))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            String body = response.body();

            if (status == 200) {
                return parseLogIdFromBody(body);
            }
            log.warn("[RemoteClient] 远程派发 HTTP {}: url={} body={}", status, url, 
                    body == null ? "" : (body.length() > 200 ? body.substring(0, 200) : body));
            return null;
        } catch (ConnectException e) {
            log.warn("[RemoteClient] 连接拒绝(节点可能已下线): url={} reason={}", url, e.getMessage());
            return null;
        } catch (HttpTimeoutException e) {
            log.warn("[RemoteClient] 请求超时: url={} timeout={}s", url, remoteConfig.getRequestTimeoutSeconds());
            return null;
        } catch (Exception e) {
            log.warn("[RemoteClient] 远程派发异常: url={} reason={}", url, e.getMessage());
            return null;
        }
    }

    /**
     * P0-1: 派发 MapReduce 子任务到远程执行器节点。
     *
     * <p>Leader 节点将子任务通过 HTTP POST 派发到执行器节点，
     * 执行器节点在本地调用 MapProcessor.process() 执行子任务，返回执行结果。
     *
     * @param node    执行器节点（含 host 和 port）
     * @param request 子任务派发请求（jobId/logId/jobKey/handler/taskName/taskParams/traceId）
     * @return 子任务执行结果 JSON（含 success/result/errorMessage）；派发失败返回 null
     */
    public String dispatchSubTask(JobNode node, RemoteSubTaskRequest request) {
        if (node == null || node.getHost() == null || node.getPort() == null) {
            log.warn("[RemoteClient] 子任务节点地址不完整, 跳过远程派发: nodeId={}",
                    node == null ? "null" : node.getNodeId());
            return null;
        }
        String url = buildSubTaskUrl(node.getHost(), node.getPort());
        String requestBody = YdszJson.toJson(request);
        CronjobProperties.Remote remoteConfig = cronjobProperties.getRemote();

        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(remoteConfig.getRequestTimeoutSeconds()))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            String body = response.body();

            if (status == 200) {
                return parseSubTaskResultFromBody(body);
            }
            log.warn("[RemoteClient] 子任务远程派发 HTTP {}: url={} body={}", status, url,
                    body == null ? "" : (body.length() > 200 ? body.substring(0, 200) : body));
            return null;
        } catch (ConnectException e) {
            log.warn("[RemoteClient] 子任务连接拒绝(节点可能已下线): url={} reason={}", url, e.getMessage());
            return null;
        } catch (HttpTimeoutException e) {
            log.warn("[RemoteClient] 子任务请求超时: url={} timeout={}s", url, remoteConfig.getRequestTimeoutSeconds());
            return null;
        } catch (Exception e) {
            log.warn("[RemoteClient] 子任务远程派发异常: url={} reason={}", url, e.getMessage());
            return null;
        }
    }

    /**
     * 构造子任务执行接口 URL。
     */
    private String buildSubTaskUrl(String host, int port) {
        return "http://" + host + ":" + port + INTERNAL_SUB_TASK_PATH;
    }

    /**
     * P0-1: 从子任务响应体解析执行结果。
     *
     * <p>响应格式为 {@code {"code":0,"data":{"success":true,"result":"...","errorMessage":null},"message":"success"}}。
     *
     * @param body HTTP 响应体
     * @return 子任务结果 JSON 字符串；解析失败返回 null
     */
    private String parseSubTaskResultFromBody(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonObject json = YdszJson.parseObjectToJsonObject(body);
            int code = json.getIntegerOrDefault("code", -1);
            if (code != 0) {
                log.warn("[RemoteClient] 子任务远程执行业务失败: code={} message={}",
                        code, json.getString("message"));
                return null;
            }
            // data 是子任务执行结果对象（含 success/result/errorMessage）
            Object data = json.get("data");
            return data == null ? null : YdszJson.toJson(data);
        } catch (Exception e) {
            log.warn("[RemoteClient] 子任务响应解析失败: body={} reason={}",
                    body.length() > 200 ? body.substring(0, 200) : body, e.getMessage());
            return null;
        }
    }

    /**
     * 构造远程执行接口 URL。
     */
    private String buildUrl(String host, int port) {
        return "http://" + host + ":" + port + INTERNAL_EXECUTE_PATH;
    }

    /**
     * 从响应体解析 logId。
     *
     * <p>响应格式为 {@code {"code":0,"data":"logId123","message":"success"}}。
     * code=0 表示成功，data 为日志 ID。
     *
     * @param body HTTP 响应体
     * @return logId；解析失败或 code!=0 返回 null
     */
    private String parseLogIdFromBody(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonObject json = YdszJson.parseObjectToJsonObject(body);
            int code = json.getIntegerOrDefault("code", -1);
            if (code != 0) {
                log.warn("[RemoteClient] 远程执行业务失败: code={} message={}",
                        code, json.getString("message"));
                return null;
            }
            String logId = json.getString("data");
            // data 可能为 null（如锁被持有、异步派发等正常跳过场景）
            return (logId == null || logId.isBlank() || "null".equals(logId)) ? null : logId;
        } catch (Exception e) {
            log.warn("[RemoteClient] 响应解析失败: body={} reason={}", 
                    body.length() > 200 ? body.substring(0, 200) : body, e.getMessage());
            return null;
        }
    }
}
