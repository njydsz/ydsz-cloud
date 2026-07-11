package com.njydsz.pmis.agent.server.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * JSON-RPC 2.0 请求信封（P3-3 落地）。
 *
 * <p>格式：
 * <pre>
 * {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{...}}
 * </pre>
 *
 * <p>通知（Notification）使用 {@code id=null}：
 * <pre>
 * {"jsonrpc":"2.0","method":"notifications/initialized"}
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-3)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JsonRpcRequest {

    /** 协议版本，固定 "2.0" */
    @Builder.Default
    private String jsonrpc = "2.0";

    /** 请求 ID（通知时为 null） */
    private Object id;

    /** 方法名，如 "initialize" / "tools/list" / "tools/call" */
    private String method;

    /** 方法参数 */
    private Map<String, Object> params;

    /**
     * 构造通知（无 id）。
     *
     * @param method 方法名
     * @return 通知请求
     */
    public static JsonRpcRequest notification(String method) {
        return JsonRpcRequest.builder().method(method).build();
    }

    /**
     * 构造通知（带参数）。
     *
     * @param method 方法名
     * @param params 参数
     * @return 通知请求
     */
    public static JsonRpcRequest notification(String method, Map<String, Object> params) {
        return JsonRpcRequest.builder().method(method).params(params).build();
    }
}
