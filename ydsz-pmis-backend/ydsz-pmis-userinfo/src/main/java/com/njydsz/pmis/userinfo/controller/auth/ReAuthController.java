package com.njydsz.pmis.userinfo.controller;

import com.njydsz.pmis.common.annotation.IdempotentExempt;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.security.SecurityContext;
import com.njydsz.pmis.userinfo.dto.auth.ReAuthRequest;
import com.njydsz.pmis.userinfo.dto.auth.ReAuthResult;
import com.njydsz.pmis.userinfo.service.auth.ReAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 敏感操作二次认证 Controller
 *
 * <p>为前端弹窗提供颁发 token 接口，前端拿到 token 后
 * 写入 {@code X-Re-Auth-Token} 请求头再调用真正的业务接口。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "二次认证")
@RestController
@RequestMapping("/user/reauth")
@RequiredArgsConstructor
@Validated
public class ReAuthController {

    /** 二次认证服务 */
    private final ReAuthService reAuthService;

    /**
     * 颁发二次认证 token
     *
     * @param request 二次认证请求参数
     * @return 统一响应结果，包含二次认证 token
     */
    @Operation(summary = "颁发二次认证 token")
    @IdempotentExempt("认证/会话/2FA 相关接口，无需幂等")
    @PostMapping("/token")
    public Result<ReAuthResult> issueToken(@Valid @RequestBody ReAuthRequest request) {
        String userId = SecurityContext.getUserId();
        return Result.ok(reAuthService.issueToken(userId, request));
    }
}
