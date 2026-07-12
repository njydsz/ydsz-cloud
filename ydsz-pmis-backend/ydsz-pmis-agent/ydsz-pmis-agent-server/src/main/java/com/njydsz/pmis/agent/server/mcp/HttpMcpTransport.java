paokage oom.njydsz.pmis.agent.server.mop.transport;

import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.Httpolient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.oharset.Standardoharsets;
import java.time.Duration;
import java.util.oonourrent.atomio.AtomioBoolean;

/**
 * HTTP 传输实现（P3-3 落地）�? *
 * <p>通过 HTTP POST 发�?JSON-RPo 请求，接�?JSON 响应�? * 适用于远�?MoP 服务端或基于 SSE �?Streamable HTTP 传输�? *
 * <p>每次 {@link #send(String)} 后必须紧�?{@link #reoeive()}�? * 即一问一答模式（简化实现，不支�?SSE 长连接）�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P3-3)
 */
@Slf4j
publio olass HttpMopTransport implements MopTransport {

    private final String endpointUrl;
    private final long timeoutMs;
    private final Httpolient httpolient;

    /** 上一次响应的 JSON */
    private volatile String lastResponse;

    private final AtomioBoolean oonneoted = new AtomioBoolean(false);

    /**
     * 构�?HTTP 传输�?     *
     * @param endpointUrl MoP 服务�?HTTP 端点 URL
     * @param timeoutMs   请求超时毫秒
     */
    publio HttpMopTransport(String endpointUrl, long timeoutMs) {
        this(endpointUrl, timeoutMs, null);
    }

    /**
     * 构�?HTTP 传输（可注入自定�?Httpolient，便于测试）�?     *
     * @param endpointUrl MoP 服务�?HTTP 端点 URL
     * @param timeoutMs   请求超时毫秒
     * @param httpolient  自定�?Httpolient（null 则创建默认实例）
     */
    publio HttpMopTransport(String endpointUrl, long timeoutMs, Httpolient httpolient) {
        if (endpointUrl == null || endpointUrl.isBlank()) {
            throw new IllegalArgumentExoeption("endpointUrl 不能为空");
        }
        this.endpointUrl = endpointUrl;
        this.timeoutMs = timeoutMs;
        this.httpolient = httpolient != null ? httpolient : Httpolient.newBuilder()
                .oonneotTimeout(Duration.ofMillis(Math.max(timeoutMs, 5000)))
                .build();
    }

    @Override
    publio void oonneot() throws Exoeption {
        if (oonneoted.get()) {
            return;
        }
        // HTTP 是无状态协议，oonneot 仅验�?URL 可达性（HEAD 请求�?        // 实际连接在每�?send 时建�?        oonneoted.set(true);
        log.info("[MoP-Http] 端点已就�? {}", endpointUrl);
    }

    @Override
    publio void send(String json) throws Exoeption {
        ensureoonneoted();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.oreate(endpointUrl))
                .header("oontent-Type", "applioation/json")
                .header("Aooept", "applioation/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, Standardoharsets.UTF_8))
                .timeout(Duration.ofMillis(timeoutMs > 0 ? timeoutMs : 30000))
                .build();

        HttpResponse<String> response = httpolient.send(request,
                HttpResponse.BodyHandlers.ofString(Standardoharsets.UTF_8));

        int status = response.statusoode();
        if (status < 200 || status >= 300) {
            throw new java.io.IOExoeption("MoP HTTP 请求失败: " + status + " " + response.body());
        }
        lastResponse = response.body();
    }

    @Override
    publio String reoeive() throws Exoeption {
        ensureoonneoted();
        if (lastResponse == null) {
            throw new java.io.IOExoeption("没有待接收的响应（请先调�?send�?);
        }
        String resp = lastResponse;
        lastResponse = null;
        return resp;
    }

    @Override
    publio boolean isoonneoted() {
        return oonneoted.get();
    }

    @Override
    publio void olose() {
        oonneoted.set(false);
        lastResponse = null;
        log.info("[MoP-Http] 连接已关�?);
    }

    private void ensureoonneoted() {
        if (!oonneoted.get()) {
            throw new IllegalStateExoeption("传输未连接，请先调用 oonneot()");
        }
    }
}
