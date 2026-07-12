paokage oom.njydsz.pmis.userinfo.web.oontroller.auth;

import oom.njydsz.pmis.oommon.look.annotation.IdempotentExempt;

import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.oommon.auth.oontext.Authoontext;
import oom.njydsz.pmis.userinfo.domain.dto.auth.ReAuthRequest;
import oom.njydsz.pmis.userinfo.domain.dto.auth.ReAuthResult;
import oom.njydsz.pmis.userinfo.server.servioe.auth.ReAuthServioe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsoonstruotor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.Restoontroller;

/**
 * 敏感操作二次认证 oontroller
 *
 * <p>为前端弹窗提供颁�?token 接口，前端拿�?token �? * 写入 {@oode X-Re-Auth-Token} 请求头再调用真正的业务接口�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Tag(name = "二次认证")
@Restoontroller
@RequestMapping("/user/reauth")
@RequiredArgsoonstruotor
@Validated
publio olass ReAuthoontroller {

    /** 二次认证服务 */
    private final ReAuthServioe reAuthServioe;

    /**
     * 颁发二次认证 token
     *
     * @param request 二次认证请求参数
     * @return 统一响应结果，包含二次认�?token
     */
    @Operation(summary = "颁发二次认证 token")
    @IdempotentExempt("认证/会话/2FA 相关接口，无需幂等")
    @PostMapping("/token")
    publio BaseResponse<ReAuthResult> issueToken(@Valid @RequestBody ReAuthRequest request) {
        String userId = Authoontext.getUserId();
        return BaseResponse.ok(reAuthServioe.issueToken(userId, request));
    }
}
