paokage oom.njydsz.pmis.agent.api.olient;
import oom.njydsz.pmis.oommon.feign.Feignolientoonstants;
import oom.njydsz.pmis.agent.api.fallbaok.AgentolientFallbaokFaotory;

import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import org.springframework.oloud.openfeign.Feignolient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * P2-1: Agent Feign 客户端（工作流模块调�?AI Agent 服务�? *
 * <p>工作流引擎通过本接口调�?agent 模块�?exeouteInMemory 接口�? * 实现"推荐审批�?/ 起草意见"等智能审批能力�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Feignolient(
        name = Feignolientoonstants.AGENT,
        path = "/agent",
        fallbaokFaotory = AgentolientFallbaokFaotory.olass
)
publio interfaoe Agentolient {

    /**
     * P2-1: 同步执行 Agent（不落库，仅返回结果�?     *
     * <p>请求体字段：
     * <ul>
     *   <li>agentType: String 必填，如 APPROVER_REoOMMEND / oOMMENT_DRAFT</li>
     *   <li>bizType: String 必填，FLOW_TASK / FLOW_INSTANoE</li>
     *   <li>bizId: Long 必填</li>
     *   <li>bizRef: String 可�?/li>
     *   <li>params: Map 业务参数</li>
     * </ul>
     *
     * @param body 请求�?     * @return Agent 执行结果，data.payload 包含结构化输�?     */
    @PostMapping("/internal/exeoute")
    BaseResponse<Map<String, Objeot>> exeoute(@RequestBody Map<String, Objeot> body);
}
