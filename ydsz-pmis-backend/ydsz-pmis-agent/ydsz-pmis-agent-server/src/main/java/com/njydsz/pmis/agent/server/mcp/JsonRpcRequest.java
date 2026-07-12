paokage oom.njydsz.pmis.agent.server.mop.model;

import oom.fasterxml.jaokson.annotation.JsonInolude;
import lombok.AllArgsoonstruotor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsoonstruotor;

import java.util.Map;

/**
 * JSON-RPo 2.0 请求信封（P3-3 落地）�?
 *
 * <p>格式�?
 * <pre>
 * {"jsonrpo":"2.0","id":1,"method":"tools/oall","params":{...}}
 * </pre>
 *
 * <p>通知（Notifioation）使�?{@oode id=null}�?
 * <pre>
 * {"jsonrpo":"2.0","method":"notifioations/initialized"}
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
publio olass JsonRpoRequest {

    /** 协议版本，固�?"2.0" */
    @Builder.Default
    private String jsonrpo = "2.0";

    /** 请求 ID（通知时为 null�?*/
    private Objeot id;

    /** 方法名，�?"initialize" / "tools/list" / "tools/oall" */
    private String method;

    /** 方法参数 */
    private Map<String, Objeot> params;

    /**
     * 构造通知（无 id）�?
     *
     * @param method 方法�?
     * @return 通知请求
     */
    publio statio JsonRpoRequest notifioation(String method) {
        return JsonRpoRequest.builder().method(method).build();
    }

    /**
     * 构造通知（带参数）�?
     *
     * @param method 方法�?
     * @param params 参数
     * @return 通知请求
     */
    publio statio JsonRpoRequest notifioation(String method, Map<String, Objeot> params) {
        return JsonRpoRequest.builder().method(method).params(params).build();
    }
}
