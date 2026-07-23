package com.njydsz.userinfo.web.controller.auth;

import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.context.AuthContext;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.IdempotentExempt;
import com.njydsz.userinfo.domain.dto.auth.TwoFactorBindResult;
import com.njydsz.userinfo.domain.entity.user.User2FADO;
import com.njydsz.userinfo.server.service.auth.TwoFactorService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 双因素认证 Controller
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Tag(name = "双因素认证")
@RestController
@RequestMapping("/user/2fa")
@RequiredArgsConstructor
@Validated
public class TwoFactorController {

    /** 双因素认证服务 */
    private final TwoFactorService service;

    /**
     * 发起 TOTP 绑定
     *
     * @return 统一响应结果，包含绑定信息（含密钥与二维码）
     */
    @Operation(summary = "发起 TOTP 绑定")
    @IdempotentExempt("认证/会话/2FA 相关接口，无需幂等")
    @PostMapping("/bind")
    public BaseResponse<TwoFactorBindResult> bind() {
        String userId = AuthContext.getUserId();
        String account = AuthContext.getUsername();
        return BaseResponse.success(service.bindTotp(userId, account));
    }

    /**
     * 校验 OTP 完成绑定
     *
     * @param otp 一次性密码
     * @return 统一响应结果，包含是否绑定成功
     */
    @Operation(summary = "校验 OTP 完成绑定")
    @IdempotentExempt("认证/会话/2FA 相关接口，无需幂等")
    @PostMapping("/confirm")
    public BaseResponse<Boolean> confirm(@RequestParam String otp) {
        String userId = AuthContext.getUserId();
        return BaseResponse.success(service.confirmBind(userId, otp));
    }

    /**
     * 校验 2FA 码（用于登录第二步）
     *
     * @param otp 一次性密码
     * @return 统一响应结果，包含是否校验通过
     */
    @Operation(summary = "校验 2FA 码（用于登录第二步）")
    @IdempotentExempt("认证/会话/2FA 相关接口，无需幂等")
    @PostMapping("/verify")
    public BaseResponse<Boolean> verify(@RequestParam String otp) {
        String userId = AuthContext.getUserId();
        return BaseResponse.success(service.verify(userId, otp));
    }

    /**
     * 使用备份码
     *
     * @param code 备份码
     * @return 统一响应结果，包含是否校验通过
     */
    @Operation(summary = "使用备份码")
    @IdempotentExempt("认证/会话/2FA 相关接口，无需幂等")
    @PostMapping("/verifyBackup")
    public BaseResponse<Boolean> verifyBackup(@RequestParam String code) {
        String userId = AuthContext.getUserId();
        return BaseResponse.success(service.verifyBackup(userId, code));
    }

    /**
     * 关闭 2FA
     *
     * @return 统一响应结果
     */
    @Operation(summary = "关闭 2FA")
    @IdempotentExempt("认证/会话/2FA 相关接口，无需幂等")
    @PostMapping("/disable")
    public BaseResponse<Void> disable() {
        String userId = AuthContext.getUserId();
        service.disable(userId);
        return BaseResponse.success();
    }

    /**
     * 查询我的 2FA 状态
     *
     * @return 统一响应结果，包含 2FA 信息
     */
    @Operation(summary = "查询我的 2FA 状态")
    @GetMapping("/me")
    public BaseResponse<User2FADO> me() {
        String userId = AuthContext.getUserId();
        return BaseResponse.success(service.find(userId));
    }

    /**
     * 查询备份码（脱敏）
     *
     * @return 统一响应结果，包含脱敏后的备份码列表
     */
    @Operation(summary = "查询备份码（脱敏）")
    @GetMapping("/backupCodes")
    public BaseResponse<List<String>> backupCodes() {
        String userId = AuthContext.getUserId();
        return BaseResponse.success(service.listBackupCodesMasked(userId));
    }
}
