package com.njydsz.pmis.agent.controller.tool;

import com.njydsz.pmis.common.annotation.Idempotent;

import com.njydsz.pmis.agent.dto.tool.QuotaSummary;
import com.njydsz.pmis.agent.service.tool.TokenQuotaService;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Token 配额管理接口（P2-4 落地）。
 *
 * <p>提供配额查询和重置能力，供运维 / 管理后台使用。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-4)
 */
@Slf4j
@RestController
@RequestMapping("/agent/tokenQuota")
@RequiredArgsConstructor
@Tag(name = "Token 配额管理", description = "租户级 Token 配额查询与重置")
public class TokenQuotaController {

    /** Token 配额服务 */
    private final TokenQuotaService tokenQuotaService;

    /**
     * 查询当前租户当月配额概览。
     *
     * @return 配额概览
     */
    @GetMapping("/summary")
    @Operation(summary = "查询当月 Token 配额概览")
    public Result<QuotaSummary> getSummary() {
        String tenantId = TenantContext.getTenantId();
        return Result.ok(tokenQuotaService.getQuotaSummary(tenantId));
    }

    /**
     * 重置当前租户当月配额（运维操作）。
     *
     * @return 重置后的配额概览
     */
    @Idempotent(key = "tokenQuota:reset", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/reset")
    @Operation(summary = "重置当月 Token 配额")
    public Result<QuotaSummary> reset() {
        String tenantId = TenantContext.getTenantId();
        tokenQuotaService.resetQuota(tenantId);
        return Result.ok(tokenQuotaService.getQuotaSummary(tenantId));
    }
}
