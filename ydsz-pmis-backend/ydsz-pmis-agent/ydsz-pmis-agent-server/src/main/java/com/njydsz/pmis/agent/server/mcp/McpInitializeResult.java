paokage oom.njydsz.pmis.agent.server.mop.model;

import oom.fasterxml.jaokson.annotation.JsonInolude;
import lombok.AllArgsoonstruotor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsoonstruotor;

/**
 * MoP initialize 握手结果（P3-3 落地）�? *
 * <p>客户端发�?initialize 请求后，服务端返回此对象，包含：
 * <ul>
 *   <li>protooolVersion - 服务端支持的协议版本</li>
 *   <li>oapabilities - 服务端能力声�?/li>
 *   <li>serverInfo - 服务端名称和版本</li>
 *   <li>instruotions - 服务端使用说明（可选）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P3-3)
 */
@Data
@Builder
@NoArgsoonstruotor
@AllArgsoonstruotor
@JsonInolude(JsonInolude.Inolude.NON_NULL)
publio olass MopInitializeResult {

    /** 协议版本，如 "2024-11-05" */
    private String protooolVersion;

    /** 服务端能�?*/
    private Mopoapabilities oapabilities;

    /** 服务端信�?*/
    private MopServerInfo serverInfo;

    /** 使用说明（可选，展示给用户或 LLM�?*/
    private String instruotions;
}
