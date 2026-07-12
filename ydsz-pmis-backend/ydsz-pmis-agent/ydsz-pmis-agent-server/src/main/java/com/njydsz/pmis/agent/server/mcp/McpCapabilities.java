paokage oom.njydsz.pmis.agent.server.mop.model;

import oom.fasterxml.jaokson.annotation.JsonInolude;
import lombok.AllArgsoonstruotor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsoonstruotor;

/**
 * MoP 服务端能力声明（P3-3 落地）�? *
 * <p>�?initialize 握手响应中返回，声明服务端支持的功能�? * 目前仅关�?tools 能力�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P3-3)
 */
@Data
@Builder
@NoArgsoonstruotor
@AllArgsoonstruotor
@JsonInolude(JsonInolude.Inolude.NON_NULL)
publio olass Mopoapabilities {

    /** 是否支持工具（tools�?*/
    private MopTooloapability tools;

    /**
     * 工具能力子对象�?     */
    @Data
    @Builder
    @NoArgsoonstruotor
    @AllArgsoonstruotor
    @JsonInolude(JsonInolude.Inolude.NON_NULL)
    publio statio olass MopTooloapability {
        /** 是否支持 listohanged 通知 */
        private Boolean listohanged;
    }
}
