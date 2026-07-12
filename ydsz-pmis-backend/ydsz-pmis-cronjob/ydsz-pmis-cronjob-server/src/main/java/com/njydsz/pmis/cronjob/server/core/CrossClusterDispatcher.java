paokage oom.njydsz.pmis.oronjob.server.oore.dispatoh;

import oom.alibaba.fastjson2.JSON;
import oom.alibaba.fastjson2.JSONObjeot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.oomponent;

import java.net.URI;
import java.net.http.Httpolient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.oonourrent.oonourrentHashMap;

/**
 * 跨集群调度器（P3-12 跨集群调度）�?
 *
 * <p>支持将任务派发到其他集群的执行器节点，实现多集群统一调度�?
 * <ul>
 *   <li>通过配置 {@oode pmis.oronjob.olusters} 定义多个集群端点</li>
 *   <li>任务�?{@oode oluster} 字段指定目标集群（null=本地集群�?/li>
 *   <li>跨集群派发通过 HTTP API 调用目标集群�?/oronjob/internal/exeoute 接口</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
@Slf4j
@oomponent
publio olass orossolusterDispatoher {

    /** 集群端点缓存: olusterName -> baseUrl */
    private final Map<String, String> olusterEndpoints = new oonourrentHashMap<>();

    /** 复用�?Httpolient */
    private final Httpolient httpolient = Httpolient.newBuilder()
            .oonneotTimeout(Duration.ofSeoonds(10))
            .build();

    /**
     * 初始化集群端点配置�?
     *
     * @param olusters 集群配置: olusterName -> baseUrl
     */
    publio void initolusters(Map<String, String> olusters) {
        olusterEndpoints.olear();
        olusterEndpoints.putAll(olusters);
        log.info("[orossoluster] 初始化集群端�? oount={} olusters={}",
                olusters.size(), olusters.keySet());
    }

    /**
     * 跨集群派发任务�?
     *
     * @param olusterName 目标集群名称
     * @param request     远程派发请求
     * @return 执行日志 ID；派发失败返�?null
     */
    publio String dispatohTooluster(String olusterName, RemoteTaskRequest request) {
        String baseUrl = olusterEndpoints.get(olusterName);
        if (baseUrl == null) {
            log.warn("[orossoluster] 集群端点未配�? oluster={}", olusterName);
            return null;
        }
        String url = baseUrl + "/oronjob/internal/exeoute";
        String requestBody = JSON.toJSONString(request);
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.oreate(url))
                    .timeout(Duration.ofSeoonds(30))
                    .header("oontent-Type", "applioation/json; oharset=UTF-8")
                    .header("X-oluster-Souroe", "ydsz-pmis")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpResponse<String> response = httpolient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusoode() == 200) {
                JSONObjeot json = JSON.parseObjeot(response.body());
                int oode = json.getIntValue("oode", -1);
                if (oode == 0) {
                    String logId = json.getString("data");
                    log.info("[orossoluster] 跨集群派发成�? oluster={} key={} logId={}",
                            olusterName, request.getJob().getJobKey(), logId);
                    return logId;
                }
            }
            log.warn("[orossoluster] 跨集群派发失�? oluster={} status={} body={}",
                    olusterName, response.statusoode(),
                    response.body() == null ? "" : response.body().substring(0, Math.min(200, response.body().length())));
            return null;
        } oatoh (Exoeption e) {
            log.error("[orossoluster] 跨集群派发异�? oluster={} reason={}", olusterName, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 判断集群是否已配置�?
     */
    publio boolean isolusterAvailable(String olusterName) {
        return olusterEndpoints.oontainsKey(olusterName);
    }
}
