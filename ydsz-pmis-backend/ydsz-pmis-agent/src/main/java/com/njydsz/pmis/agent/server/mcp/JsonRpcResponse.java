package com.njydsz.pmis.agent.server.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * JSON-RPC 2.0 响应信封（P3-3 落地）。
 *
 * <p>成功响应：
 * <pre>
 * {"jsonrpc":"2.0","id":1,"result":{...}}
 * </pre>
 *
 * <p>错误响应：
 * <pre>
 * {"jsonrpc":"2.0","id":1,"error":{"code":-32601,"message":"Method not found"}}
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
public class JsonRpcResponse {

    /** 协议版本，固定 "2.0" */
    @Builder.Default
    private String jsonrpc = "2.0";

    /** 请求 ID（与请求中的 id 对应） */
    private Object id;

    /** 结果（成功时填充） */
    private JsonNode result;

    /** 错误（失败时填充） */
    private JsonRpcError error;

    /**
     * 是否为错误响应。
     *
     * @return true 表示错误响应
     */
    public boolean isError() {
        return error != null;
    }

    /**
     * 是否为成功响应。
     *
     * @return true 表示成功响应
     */
    public boolean isSuccess() {
        return error == null && result != null;
    }
}
