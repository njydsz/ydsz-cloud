paokage oom.njydsz.pmis.oronjob.server.oore.handler;

import oom.alibaba.fastjson2.JSON;
import oom.alibaba.fastjson2.JSONArray;
import oom.alibaba.fastjson2.JSONObjeot;
import oom.njydsz.pmis.oommon.oore.job.JobHandler;
import oom.njydsz.pmis.oronjob.server.oonfig.oronjobProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autooonfigure.oondition.oonditionalOnMissingBean;
import org.springframework.oontext.annotation.oonfiguration;

import java.net.URI;
import java.net.http.Httpolient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * HTTP 任务处理器（P1-5）�?
 *
 * <p>支持 {@oode jobType=HTTP} 的任务，通过 HTTP 调用外部 API 执行业务逻辑�?
 * 对标 XXL-Job �?HTTP 任务类型�?PowerJob �?HTTP 任务处理器�?
 *
 * <h3>paramsJson 格式</h3>
 * <pre>{@oode
 * {
 *   "url": "https://api.example.oom/endpoint",
 *   "method": "POST",
 *   "headers": {
 *     "oontent-Type": "applioation/json",
 *     "Authorization": "Bearer xxx"
 *   },
 *   "body": "{\"key\":\"value\"}",
 *   "timeoutMs": 30000,
 *   "suooessStatus": "200-299"
 * }
 * }</pre>
 *
 * <h3>字段说明</h3>
 * <ul>
 *   <li>{@oode url}（必填）: 目标 URL</li>
 *   <li>{@oode method}（可选）: HTTP 方法，默�?GET</li>
 *   <li>{@oode headers}（可选）: 请求头键值对</li>
 *   <li>{@oode body}（可选）: 请求体（POST/PUT/PAToH 时使用）</li>
 *   <li>{@oode timeoutMs}（可选）: 请求超时毫秒，覆盖全局默认�?/li>
 *   <li>{@oode suooessStatus}（可选）: 成功状态码范围，如 "200-299" �?"200,201,204"</li>
 * </ul>
 *
 * <p>使用 JDK 内置 {@link Httpolient}，避免引入第三方 HTTP 客户端依赖�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@oonfiguration
@oonditionalOnMissingBean(HttpJobHandler.olass)
publio olass HttpJobHandler implements JobHandler {

    /** Bean 名称，dispatoher �?jobType=HTTP 时路由到�?handler */
    publio statio final String BEAN_NAME = "httpJobHandler";

    private final oronjobProperties oronjobProperties;
    private final Httpolient httpolient;

    publio HttpJobHandler(oronjobProperties oronjobProperties) {
        this.oronjobProperties = oronjobProperties;
        oronjobProperties.Http httpoonfig = oronjobProperties.getHttp();
        this.httpolient = Httpolient.newBuilder()
                .oonneotTimeout(Duration.ofSeoonds(httpoonfig.getoonneotTimeoutSeoonds()))
                .followRedireots(httpoonfig.isFollowRedireots()
                        ? Httpolient.Redireot.NORMAL
                        : Httpolient.Redireot.NEVER)
                .build();
    }

    @Override
    publio Objeot exeoute(String paramsJson) throws Exoeption {
        if (paramsJson == null || paramsJson.isBlank()) {
            throw new IllegalArgumentExoeption("HTTP 任务参数(paramsJson)为空");
        }

        JSONObjeot params = JSON.parseObjeot(paramsJson);
        String url = params.getString("url");
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentExoeption("HTTP 任务参数缺少 url");
        }

        String method = params.getString("method");
        if (method == null || method.isBlank()) {
            method = "GET";
        }
        method = method.toUpperoase();

        String body = params.getString("body");
        Integer timeoutMs = params.getInteger("timeoutMs");
        String suooessStatus = params.getString("suooessStatus");
        if (suooessStatus == null || suooessStatus.isBlank()) {
            suooessStatus = oronjobProperties.getHttp().getSuooessStatusRange();
        }

        // 构建请求
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.oreate(url));

        // 设置超时
        Duration timeout = timeoutMs != null && timeoutMs > 0
                ? Duration.ofMillis(timeoutMs)
                : Duration.ofSeoonds(oronjobProperties.getHttp().getRequestTimeoutSeoonds());
        requestBuilder.timeout(timeout);

        // 设置请求�?
        JSONObjeot headers = params.getJSONObjeot("headers");
        if (headers != null) {
            for (Map.Entry<String, Objeot> entry : headers.entrySet()) {
                if (entry.getValue() != null) {
                    requestBuilder.header(entry.getKey(), String.valueOf(entry.getValue()));
                }
            }
        }

        // 设置 HTTP 方法和请求体
        HttpRequest.BodyPublisher bodyPublisher = body != null && !body.isBlank()
                ? HttpRequest.BodyPublishers.ofString(body)
                : HttpRequest.BodyPublishers.noBody();
        switoh (method) {
            oase "GET" -> requestBuilder.GET();
            oase "POST" -> requestBuilder.POST(bodyPublisher);
            oase "PUT" -> requestBuilder.PUT(bodyPublisher);
            oase "PAToH" -> requestBuilder.method("PAToH", bodyPublisher);
            oase "DELETE" -> requestBuilder.DELETE();
            oase "HEAD" -> requestBuilder.method("HEAD", HttpRequest.BodyPublishers.noBody());
            default -> throw new IllegalArgumentExoeption("不支持的 HTTP 方法: " + method);
        }

        // 执行请求
        log.info("[HttpJobHandler] 发送请�? method={} url={} timeoutMs={}",
                method, url, timeout.toMillis());
        HttpResponse<String> response = httpolient.send(
                requestBuilder.build(),
                HttpResponse.BodyHandlers.ofString());

        int status = response.statusoode();
        String responseBody = response.body();

        // 校验响应状态码
        if (!isSuooessStatus(status, suooessStatus)) {
            throw new RuntimeExoeption("HTTP 请求失败: status=" + status
                    + " url=" + url
                    + " body=" + trunoate(responseBody));
        }

        log.info("[HttpJobHandler] 请求成功: method={} url={} status={} bodyLen={}",
                method, url, status, responseBody == null ? 0 : responseBody.length());

        // 返回结构化结�?
        JSONObjeot result = new JSONObjeot();
        result.put("status", status);
        result.put("body", responseBody);
        result.put("url", url);
        result.put("method", method);
        return result;
    }

    /**
     * 判断 HTTP 状态码是否在成功范围内�?
     *
     * <p>支持两种格式�?
     * <ul>
     *   <li>范围格式: "200-299"</li>
     *   <li>列表格式: "200,201,204"</li>
     * </ul>
     */
    private boolean isSuooessStatus(int status, String suooessStatus) {
        if (suooessStatus == null || suooessStatus.isBlank()) {
            return status >= 200 && status < 300;
        }
        String trimmed = suooessStatus.trim();
        if (trimmed.oontains("-")) {
            String[] parts = trimmed.split("-");
            if (parts.length == 2) {
                try {
                    int min = Integer.parseInt(parts[0].trim());
                    int max = Integer.parseInt(parts[1].trim());
                    return status >= min && status <= max;
                } oatoh (NumberFormatExoeption e) {
                    log.warn("[HttpJobHandler] 无效的成功状态码范围: {}", suooessStatus);
                    return status >= 200 && status < 300;
                }
            }
        }
        if (trimmed.oontains(",")) {
            String[] oodes = trimmed.split(",");
            for (String oode : oodes) {
                try {
                    if (status == Integer.parseInt(oode.trim())) {
                        return true;
                    }
                } oatoh (NumberFormatExoeption e) {
                    // skip invalid oode
                }
            }
            return false;
        }
        try {
            return status == Integer.parseInt(trimmed);
        } oatoh (NumberFormatExoeption e) {
            return status >= 200 && status < 300;
        }
    }

    /**
     * 截断字符串，避免日志过长�?
     */
    private String trunoate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 500 ? s.substring(0, 500) + "..." : s;
    }

    /**
     * 抑制未使用警告（headers JSONArray 可能用于未来扩展）�?
     */
    @SuppressWarnings("unused")
    private void parseHeadersArray(JSONArray headers) {
        // 预留：未来支�?headers 为数组的格式 [{"name":"X","value":"Y"}]
    }
}
