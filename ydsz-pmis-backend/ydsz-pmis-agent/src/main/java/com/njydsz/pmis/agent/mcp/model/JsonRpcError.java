package com.njydsz.pmis.agent.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * JSON-RPC 2.0 错误对象（P3-3 落地）。
 *
 * <p>标准错误码：
 * <ul>
 *   <li>-32700 Parse error（JSON 解析失败）</li>
 *   <li>-32600 Invalid Request（无效请求）</li>
 *   <li>-32601 Method not found（方法不存在）</li>
 *   <li>-32602 Invalid params（参数无效）</li>
 *   <li>-32603 Internal error（内部错误）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-3)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JsonRpcError {

    /** 错误码 */
    private int code;

    /** 错误消息 */
    private String message;

    /** 附加数据（可选） */
    private Object data;

    /**
     * 构造方法未找到错误。
     *
     * @return 错误对象
     */
    public static JsonRpcError methodNotFound() {
        return JsonRpcError.builder()
                .code(-32601)
                .message("Method not found")
                .build();
    }

    /**
     * 构造内部错误。
     *
     * @param message 错误消息
     * @return 错误对象
     */
    public static JsonRpcError internalError(String message) {
        return JsonRpcError.builder()
                .code(-32603)
                .message(message != null ? message : "Internal error")
                .build();
    }
}
