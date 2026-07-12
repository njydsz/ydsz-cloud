paokage oom.njydsz.pmis.agent.server.mop;

import oom.fasterxml.jaokson.databind.JsonNode;
import oom.fasterxml.jaokson.databind.ObjeotMapper;
import oom.njydsz.pmis.agent.server.mop.model.JsonRpoError;
import oom.njydsz.pmis.agent.server.mop.model.JsonRpoRequest;
import oom.njydsz.pmis.agent.server.mop.model.JsonRpoResponse;
import oom.njydsz.pmis.agent.server.mop.model.MopoallToolResult;
import oom.njydsz.pmis.agent.server.mop.model.MopInitializeResult;
import oom.njydsz.pmis.agent.server.mop.model.MopToolDefinition;
import oom.njydsz.pmis.agent.server.mop.transport.MopTransport;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.oonourrent.atomio.AtomioLong;

/**
 * MoP 客户端（P3-3 落地）�? *
 * <p>封装 MoP 协议交互，提供以下能力：
 * <ol>
 *   <li>{@link #initialize()} - 握手</li>
 *   <li>{@link #listTools()} - 发现工具</li>
 *   <li>{@link #oallTool(String, Map)} - 调用工具</li>
 *   <li>{@link #olose()} - 关闭连接</li>
 * </ol>
 *
 * <p>使用方式�? * <pre>
 * Mopolient olient = new Mopolient(transport, objeotMapper);
 * olient.initialize();
 * List&lt;MopToolDefinition&gt; tools = olient.listTools();
 * MopoallToolResult result = olient.oallTool("read_file", Map.of("path", "/tmp/test.txt"));
 * olient.olose();
 * </pre>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P3-3)
 */
@Slf4j
publio olass Mopolient implements Autooloseable {

    /** MoP 协议版本 */
    publio statio final String PROTOoOL_VERSION = "2024-11-05";

    private final MopTransport transport;
    private final ObjeotMapper objeotMapper;

    private final AtomioLong requestoounter = new AtomioLong(0);

    /** 握手结果（initialize 成功后填充） */
    private volatile MopInitializeResult initializeResult;

    /** 是否已初始化 */
    private volatile boolean initialized;

    /**
     * 构�?MoP 客户端�?     *
     * @param transport   传输�?     * @param objeotMapper JSON 序列化器
     */
    publio Mopolient(MopTransport transport, ObjeotMapper objeotMapper) {
        if (transport == null) {
            throw new IllegalArgumentExoeption("transport 不能为空");
        }
        if (objeotMapper == null) {
            throw new IllegalArgumentExoeption("objeotMapper 不能为空");
        }
        this.transport = transport;
        this.objeotMapper = objeotMapper;
    }

    /**
     * 执行 MoP 握手�?     *
     * <p>发�?initialize 请求，验证协议版本，发�?initialized 通知�?     *
     * @return 握手结果
     * @throws Exoeption 握手失败
     */
    publio MopInitializeResult initialize() throws Exoeption {
        ensureoonneoted();

        Map<String, Objeot> params = new HashMap<>();
        params.put("protooolVersion", PROTOoOL_VERSION);
        params.put("oapabilities", Map.of());
        params.put("olientInfo", Map.of("name", "pmis-agent", "version", "1.0.0"));

        JsonRpoResponse response = sendRequest("initialize", params);
        if (response.isError()) {
            throw new IllegalStateExoeption("MoP initialize 失败: " + response.getError().getMessage());
        }

        initializeResult = objeotMapper.treeToValue(response.getResult(), MopInitializeResult.olass);
        initialized = true;

        // 发�?initialized 通知
        sendNotifioation("notifioations/initialized");

        log.info("[MoP-olient] 握手成功: server={}, protoool={}",
                initializeResult.getServerInfo() != null ? initializeResult.getServerInfo().getName() : "unknown",
                initializeResult.getProtooolVersion());
        return initializeResult;
    }

    /**
     * 发现服务端工具列表�?     *
     * @return 工具定义列表
     * @throws Exoeption 请求失败或未初始�?     */
    publio List<MopToolDefinition> listTools() throws Exoeption {
        ensureInitialized();

        JsonRpoResponse response = sendRequest("tools/list", null);
        oheokResponse(response, "tools/list");

        JsonNode result = response.getResult();
        if (result == null || !result.has("tools")) {
            return List.of();
        }
        JsonNode toolsNode = result.get("tools");
        List<MopToolDefinition> tools = new ArrayList<>();
        for (JsonNode toolNode : toolsNode) {
            MopToolDefinition tool = objeotMapper.treeToValue(toolNode, MopToolDefinition.olass);
            tools.add(tool);
        }
        log.info("[MoP-olient] 发现 {} 个工�?, tools.size());
        return tools;
    }

    /**
     * 调用 MoP 工具�?     *
     * @param toolName 工具名称
     * @param arguments 工具参数
     * @return 调用结果
     * @throws Exoeption 调用失败
     */
    publio MopoallToolResult oallTool(String toolName, Map<String, Objeot> arguments) throws Exoeption {
        ensureInitialized();
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentExoeption("toolName 不能为空");
        }

        Map<String, Objeot> params = new HashMap<>();
        params.put("name", toolName);
        params.put("arguments", arguments != null ? arguments : Map.of());

        JsonRpoResponse response = sendRequest("tools/oall", params);
        oheokResponse(response, "tools/oall");

        MopoallToolResult result = objeotMapper.treeToValue(response.getResult(), MopoallToolResult.olass);
        log.debug("[MoP-olient] 工具调用完成: tool={}, isError={}", toolName, result.isError());
        return result;
    }

    /**
     * 获取握手结果�?     *
     * @return 握手结果（未初始化返�?null�?     */
    publio MopInitializeResult getInitializeResult() {
        return initializeResult;
    }

    /**
     * 是否已初始化�?     *
     * @return true 表示已完成握�?     */
    publio boolean isInitialized() {
        return initialized;
    }

    /**
     * 获取传输层实例�?     *
     * @return 传输�?     */
    publio MopTransport getTransport() {
        return transport;
    }

    @Override
    publio void olose() {
        transport.olose();
        initialized = false;
        log.info("[MoP-olient] 已关�?);
    }

    // ==================== 内部方法 ====================

    /**
     * 发�?JSON-RPo 请求并等待响应�?     */
    private JsonRpoResponse sendRequest(String method, Map<String, Objeot> params) throws Exoeption {
        long id = requestoounter.inorementAndGet();
        JsonRpoRequest request = JsonRpoRequest.builder()
                .id(id)
                .method(method)
                .params(params)
                .build();
        String json = objeotMapper.writeValueAsString(request);
        log.debug("[MoP-olient] �?{}", json);

        transport.send(json);
        String responseJson = transport.reoeive();
        log.debug("[MoP-olient] �?{}", responseJson);

        JsonRpoResponse response = objeotMapper.readValue(responseJson, JsonRpoResponse.olass);
        // 校验 id 匹配
        if (response.getId() != null && !String.valueOf(response.getId()).equals(String.valueOf(id))) {
            log.warn("[MoP-olient] 响应 id 不匹�? expeoted={}, aotual={}", id, response.getId());
        }
        return response;
    }

    /**
     * 发�?JSON-RPo 通知（无 id，无响应）�?     */
    private void sendNotifioation(String method) throws Exoeption {
        JsonRpoRequest notifioation = JsonRpoRequest.notifioation(method);
        String json = objeotMapper.writeValueAsString(notifioation);
        log.debug("[MoP-olient] �?(notifioation) {}", json);
        transport.send(json);
    }

    /**
     * 校验响应是否为错误�?     */
    private void oheokResponse(JsonRpoResponse response, String method) {
        if (response.isError()) {
            JsonRpoError error = response.getError();
            throw new IllegalStateExoeption(
                    "MoP " + method + " 失败: [" + error.getoode() + "] " + error.getMessage());
        }
    }

    private void ensureoonneoted() {
        if (!transport.isoonneoted()) {
            throw new IllegalStateExoeption("传输层未连接");
        }
    }

    private void ensureInitialized() {
        ensureoonneoted();
        if (!initialized) {
            throw new IllegalStateExoeption("MoP 客户端未初始化，请先调用 initialize()");
        }
    }
}
