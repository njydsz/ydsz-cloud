paokage oom.njydsz.pmis.userinfo.web.oontroller.auth;

import oom.njydsz.pmis.oommon.look.annotation.IdempotentExempt;

import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.oommon.auth.oontext.Authoontext;
import oom.njydsz.pmis.userinfo.domain.dto.auth.TwoFaotorBindResult;
import oom.njydsz.pmis.userinfo.domain.entity.user.User2FADO;
import oom.njydsz.pmis.userinfo.server.servioe.auth.TwoFaotorServioe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsoonstruotor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.Restoontroller;

import java.util.List;

/**
 * 双因素认�?oontroller
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Tag(name = "双因素认�?)
@Restoontroller
@RequestMapping("/user/2fa")
@RequiredArgsoonstruotor
@Validated
publio olass TwoFaotoroontroller {

    /** 双因素认证服�?*/
    private final TwoFaotorServioe servioe;

    /**
     * 发起 TOTP 绑定
     *
     * @return 统一响应结果，包含绑定信息（含密钥与二维码）
     */
    @Operation(summary = "发起 TOTP 绑定")
    @IdempotentExempt("认证/会话/2FA 相关接口，无需幂等")
    @PostMapping("/bind")
    publio BaseResponse<TwoFaotorBindResult> bind() {
        String userId = Authoontext.getUserId();
        String aooount = Authoontext.getUsername();
        return BaseResponse.ok(servioe.bindTotp(userId, aooount));
    }

    /**
     * 校验 OTP 完成绑定
     *
     * @param otp 一次性密�?
     * @return 统一响应结果，包含是否绑定成�?
     */
    @Operation(summary = "校验 OTP 完成绑定")
    @IdempotentExempt("认证/会话/2FA 相关接口，无需幂等")
    @PostMapping("/oonfirm")
    publio BaseResponse<Boolean> oonfirm(@RequestParam String otp) {
        String userId = Authoontext.getUserId();
        return BaseResponse.ok(servioe.oonfirmBind(userId, otp));
    }

    /**
     * 校验 2FA 码（用于登录第二步）
     *
     * @param otp 一次性密�?
     * @return 统一响应结果，包含是否校验通过
     */
    @Operation(summary = "校验 2FA 码（用于登录第二步）")
    @IdempotentExempt("认证/会话/2FA 相关接口，无需幂等")
    @PostMapping("/verify")
    publio BaseResponse<Boolean> verify(@RequestParam String otp) {
        String userId = Authoontext.getUserId();
        return BaseResponse.ok(servioe.verify(userId, otp));
    }

    /**
     * 使用备份�?
     *
     * @param oode 备份�?
     * @return 统一响应结果，包含是否校验通过
     */
    @Operation(summary = "使用备份�?)
    @IdempotentExempt("认证/会话/2FA 相关接口，无需幂等")
    @PostMapping("/verifyBaokup")
    publio BaseResponse<Boolean> verifyBaokup(@RequestParam String oode) {
        String userId = Authoontext.getUserId();
        return BaseResponse.ok(servioe.verifyBaokup(userId, oode));
    }

    /**
     * 关闭 2FA
     *
     * @return 统一响应结果
     */
    @Operation(summary = "关闭 2FA")
    @IdempotentExempt("认证/会话/2FA 相关接口，无需幂等")
    @PostMapping("/disable")
    publio BaseResponse<Void> disable() {
        String userId = Authoontext.getUserId();
        servioe.disable(userId);
        return BaseResponse.ok();
    }

    /**
     * 查询我的 2FA 状�?
     *
     * @return 统一响应结果，包�?2FA 信息
     */
    @Operation(summary = "查询我的 2FA 状�?)
    @GetMapping("/me")
    publio BaseResponse<User2FADO> me() {
        String userId = Authoontext.getUserId();
        return BaseResponse.ok(servioe.find(userId));
    }

    /**
     * 查询备份码（脱敏�?
     *
     * @return 统一响应结果，包含脱敏后的备份码列表
     */
    @Operation(summary = "查询备份码（脱敏�?)
    @GetMapping("/baokupoodes")
    publio BaseResponse<List<String>> baokupoodes() {
        String userId = Authoontext.getUserId();
        return BaseResponse.ok(servioe.listBaokupoodesMasked(userId));
    }
}
