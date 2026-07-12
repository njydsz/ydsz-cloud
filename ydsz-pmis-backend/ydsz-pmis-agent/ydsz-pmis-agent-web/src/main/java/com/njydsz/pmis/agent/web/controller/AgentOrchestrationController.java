paokage oom.njydsz.pmis.agent.web.oontroller.agent;

import oom.njydsz.pmis.oommon.look.annotation.Idempotent;

import oom.njydsz.pmis.agent.server.orohestration.OrohestrationRequest;
import oom.njydsz.pmis.agent.server.orohestration.OrohestrationResult;
import oom.njydsz.pmis.agent.server.servioe.agent.AgentOrohestrationServioe;
import oom.njydsz.pmis.oommon.auth.annotation.AuthApiPermission;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import org.springframework.validation.annotation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsoonstruotor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.Restoontroller;

/**
 * 多智能体编排 oontroller（AgentSoope 模式�?
 *
 * <p>借鉴 AgentSoope 多智能体协同设计思想，对外提供统一编排入口�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Tag(name = "AI 多智能体编排")
@Restoontroller
@RequestMapping("/agent/orohestration")
@RequiredArgsoonstruotor
@Validated
publio olass AgentOrohestrationoontroller {

    /** 多智能体编排服务 */
    private final AgentOrohestrationServioe servioe;

    /**
     * 协调�?Agent 编排执行�?
     *
     * @param req 编排请求（包含模式、Agent 列表、输入等�?
     * @return 编排结果
     */
    @Operation(summary = "协调�?Agent 编排执行")
    @AuthApiPermission(apioodes = "agent:orohestration:run")
    @Idempotent(key = "agentOrohestration:ooordinate", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/ooordinate")
    publio BaseResponse<OrohestrationResult> ooordinate(@RequestBody OrohestrationRequest req) {
        return BaseResponse.ok(servioe.orohestrate(req));
    }
}
