package com.njydsz.pmis.user.controller;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.security.SecurityContext;
import com.njydsz.pmis.user.dto.ReAuthRequest;
import com.njydsz.pmis.user.dto.ReAuthResult;
import com.njydsz.pmis.user.service.ReAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/v1/user/reauth")
@RequiredArgsConstructor
public class ReAuthController {

    private final ReAuthService reAuthService;

    @Operation(summary = "颁发二次认证 token")
    @PostMapping("/token")
    public Result<ReAuthResult> issueToken(@Valid @RequestBody ReAuthRequest request) {
        Long userId = SecurityContext.getUserId();
        return Result.ok(reAuthService.issueToken(userId, request));
    }
}
