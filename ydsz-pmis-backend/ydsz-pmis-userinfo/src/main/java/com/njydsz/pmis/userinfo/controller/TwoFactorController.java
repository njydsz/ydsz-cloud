package com.njydsz.pmis.userinfo.controller;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.security.SecurityContext;
import com.njydsz.pmis.userinfo.dto.TwoFactorBindResult;
import com.njydsz.pmis.userinfo.entity.User2FADO;
import com.njydsz.pmis.userinfo.service.TwoFactorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 双因素认证 Controller
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "双因素认证")
@RestController
@RequestMapping("/api/v1/user/2fa")
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
    @PostMapping("/bind")
    public Result<TwoFactorBindResult> bind() {
        Long userId = SecurityContext.getUserId();
        String account = SecurityContext.getUsername();
        return Result.ok(service.bindTotp(userId, account));
    }

    /**
     * 校验 OTP 完成绑定
     *
     * @param otp 一次性密码
     * @return 统一响应结果，包含是否绑定成功
     */
    @Operation(summary = "校验 OTP 完成绑定")
    @PostMapping("/confirm")
    public Result<Boolean> confirm(@RequestParam String otp) {
        Long userId = SecurityContext.getUserId();
        return Result.ok(service.confirmBind(userId, otp));
    }

    /**
     * 校验 2FA 码（用于登录第二步）
     *
     * @param otp 一次性密码
     * @return 统一响应结果，包含是否校验通过
     */
    @Operation(summary = "校验 2FA 码（用于登录第二步）")
    @PostMapping("/verify")
    public Result<Boolean> verify(@RequestParam String otp) {
        Long userId = SecurityContext.getUserId();
        return Result.ok(service.verify(userId, otp));
    }

    /**
     * 使用备份码
     *
     * @param code 备份码
     * @return 统一响应结果，包含是否校验通过
     */
    @Operation(summary = "使用备份码")
    @PostMapping("/verify-backup")
    public Result<Boolean> verifyBackup(@RequestParam String code) {
        Long userId = SecurityContext.getUserId();
        return Result.ok(service.verifyBackup(userId, code));
    }

    /**
     * 关闭 2FA
     *
     * @return 统一响应结果
     */
    @Operation(summary = "关闭 2FA")
    @PostMapping("/disable")
    public Result<Void> disable() {
        Long userId = SecurityContext.getUserId();
        service.disable(userId);
        return Result.ok();
    }

    /**
     * 查询我的 2FA 状态
     *
     * @return 统一响应结果，包含 2FA 信息
     */
    @Operation(summary = "查询我的 2FA 状态")
    @GetMapping("/me")
    public Result<User2FADO> me() {
        Long userId = SecurityContext.getUserId();
        return Result.ok(service.find(userId));
    }

    /**
     * 查询备份码（脱敏）
     *
     * @return 统一响应结果，包含脱敏后的备份码列表
     */
    @Operation(summary = "查询备份码（脱敏）")
    @GetMapping("/backup-codes")
    public Result<List<String>> backupCodes() {
        Long userId = SecurityContext.getUserId();
        return Result.ok(service.listBackupCodesMasked(userId));
    }
}
