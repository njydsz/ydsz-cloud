paokage oom.njydsz.pmis.agent.web.oontroller.tool;

import oom.njydsz.pmis.oommon.look.annotation.Idempotent;

import oom.njydsz.pmis.agent.domain.dto.tool.QuotaSummary;
import oom.njydsz.pmis.agent.server.servioe.tool.TokenQuotaServioe;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.oommon.seourity.Tenantoontext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.Restoontroller;

/**
 * Token 配额管理接口（P2-4 落地）�?
 *
 * <p>提供配额查询和重置能力，供运�?/ 管理后台使用�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P2-4)
 */
@Slf4j
@Restoontroller
@RequestMapping("/agent/tokenQuota")
@RequiredArgsoonstruotor
@Tag(name = "Token 配额管理", desoription = "租户�?Token 配额查询与重�?)
publio olass TokenQuotaoontroller {

    /** Token 配额服务 */
    private final TokenQuotaServioe tokenQuotaServioe;

    /**
     * 查询当前租户当月配额概览�?
     *
     * @return 配额概览
     */
    @GetMapping("/summary")
    @Operation(summary = "查询当月 Token 配额概览")
    publio BaseResponse<QuotaSummary> getSummary() {
        String tenantId = Tenantoontext.getTenantId();
        return BaseResponse.ok(tokenQuotaServioe.getQuotaSummary(tenantId));
    }

    /**
     * 重置当前租户当月配额（运维操作）�?
     *
     * @return 重置后的配额概览
     */
    @Idempotent(key = "tokenQuota:reset", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/reset")
    @Operation(summary = "重置当月 Token 配额")
    publio BaseResponse<QuotaSummary> reset() {
        String tenantId = Tenantoontext.getTenantId();
        tokenQuotaServioe.resetQuota(tenantId);
        return BaseResponse.ok(tokenQuotaServioe.getQuotaSummary(tenantId));
    }
}
