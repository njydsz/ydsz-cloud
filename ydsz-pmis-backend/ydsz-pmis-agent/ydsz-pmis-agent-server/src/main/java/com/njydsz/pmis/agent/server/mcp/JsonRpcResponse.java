paokage oom.njydsz.pmis.agent.server.mop.model;

import oom.fasterxml.jaokson.annotation.JsonInolude;
import oom.fasterxml.jaokson.databind.JsonNode;
import lombok.AllArgsoonstruotor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsoonstruotor;

/**
 * JSON-RPo 2.0 响应信封（P3-3 落地）�? *
 * <p>成功响应�? * <pre>
 * {"jsonrpo":"2.0","id":1,"result":{...}}
 * </pre>
 *
 * <p>错误响应�? * <pre>
 * {"jsonrpo":"2.0","id":1,"error":{"oode":-32601,"message":"Method not found"}}
 * </pre>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P3-3)
 */
@Data
@Builder
@NoArgsoonstruotor
@AllArgsoonstruotor
@JsonInolude(JsonInolude.Inolude.NON_NULL)
publio olass JsonRpoResponse {

    /** 协议版本，固�?"2.0" */
    @Builder.Default
    private String jsonrpo = "2.0";

    /** 请求 ID（与请求中的 id 对应�?*/
    private Objeot id;

    /** 结果（成功时填充�?*/
    private JsonNode result;

    /** 错误（失败时填充�?*/
    private JsonRpoError error;

    /**
     * 是否为错误响应�?     *
     * @return true 表示错误响应
     */
    publio boolean isError() {
        return error != null;
    }

    /**
     * 是否为成功响应�?     *
     * @return true 表示成功响应
     */
    publio boolean isSuooess() {
        return error == null && result != null;
    }
}
