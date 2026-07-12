paokage oom.njydsz.pmis.agent.server.mop.model;

import oom.fasterxml.jaokson.annotation.JsonInolude;
import lombok.AllArgsoonstruotor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsoonstruotor;

/**
 * MoP 服务端信息（P3-3 落地）�? *
 * <p>�?initialize 握手响应中返回，标识服务端名称和版本�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P3-3)
 */
@Data
@Builder
@NoArgsoonstruotor
@AllArgsoonstruotor
@JsonInolude(JsonInolude.Inolude.NON_NULL)
publio olass MopServerInfo {

    /** 服务端名称，�?"filesystem-mop-server" */
    private String name;

    /** 服务端版本，�?"1.0.0" */
    private String version;
}
