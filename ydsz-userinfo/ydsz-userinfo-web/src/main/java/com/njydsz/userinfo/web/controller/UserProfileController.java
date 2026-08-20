package com.njydsz.userinfo.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.userinfo.domain.dto.ChangePasswordDTO;
import com.njydsz.userinfo.domain.dto.MfaOperationDTO;
import com.njydsz.userinfo.domain.dto.UserProfileUpdateDTO;
import com.njydsz.userinfo.domain.vo.MfaSetupVO;
import com.njydsz.userinfo.domain.vo.UserAccountVO;
import com.njydsz.userinfo.server.auth.MfaService;
import com.njydsz.userinfo.server.service.UserAccountService;

/**
 * 用户资料 Controller（个人中心）。
 *
 * <p>提供当前登录用户的个人资料管理能力，包括：查看资料、修改基本信息、更新头像、 双因素认证（MFA）绑定管理。
 *
 * <p><b>接口路径：</b>{@code /api/v1/profile}
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
@Tag(name = "个人中心", description = "用户个人资料管理")
public class UserProfileController {

  /** 用户账号服务（密码修改/资料更新） */
  private final UserAccountService userAccountService;

  /** 双因素认证服务（TOTP 绑定/激活/解除） */
  private final MfaService mfaService;

  /**
   * 获取当前登录用户的个人资料。
   *
   * @return 用户资料 VO
   */
  @GetMapping("/me")
  @Operation(summary = "获取当前用户资料")
  public YdszResponse<UserAccountVO> getCurrentUserProfile() {
    String userId = RequestContext.getUserId();
    UserAccountVO user = userAccountService.getById(userId);
    return YdszResponse.success(user);
  }

  /**
   * 更新当前登录用户的资料。
   *
   * <p>仅更新用户可自助修改的字段（realName/phone/email/avatar），不涉及状态、角色等管理字段。
   *
   * @param dto 更新内容
   * @return 是否成功
   */
  @PutMapping("/me")
  @Operation(summary = "更新当前用户资料")
  public YdszResponse<Boolean> updateCurrentUserProfile(@RequestBody UserProfileUpdateDTO dto) {
    String userId = RequestContext.getUserId();
    boolean result = userAccountService.updateProfile(userId, dto);
    log.info("用户资料更新成功: userId={}", userId);
    return YdszResponse.success(result);
  }

  /**
   * 当前登录用户修改密码（需验证旧密码）。
   *
   * <p>用户自行修改登录密码，需输入旧密码进行身份验证。修改成功后，所有活跃会话保持不变。
   *
   * <p><b>安全策略：</b>
   *
   * <ul>
   *   <li>旧密码验证（BCrypt 比对）</li>
   *   <li>新密码须符合密码策略（长度+复杂度）</li>
   *   <li>新密码不能与最近使用过的密码重复</li>
   *   <li>限流 3 QPS（防暴力破解）</li>
   * </ul>
   *
   * @param dto 修改密码请求（含旧密码和新密码）
   * @return 是否成功
   */
  @Audit(
      module = "个人中心",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'用户修改密码'",
      excludeParams = {"oldPassword", "newPassword"})
  @PutMapping("/password")
  @Operation(summary = "修改密码", description = "当前登录用户修改密码（需验证旧密码）")
  @RateLimit(resource = "userinfo.profile.changePassword", threshold = 3)
  public YdszResponse<Boolean> changePassword(@jakarta.validation.Valid @RequestBody ChangePasswordDTO dto) {
    String userId = RequestContext.getUserId();
    // 确保只能修改自己的密码
    dto.setUserId(userId);
    boolean result = userAccountService.changePassword(dto);
    log.info("用户密码修改成功: userId={}", userId);
    return YdszResponse.success(result);
  }

  /**
   * 上传头像。
   *
   * <p>接收头像图片文件，存储到文件服务，返回头像 URL。 注意：当前实现仅返回一个模拟 URL，生产环境需集成 common-file 文件服务。
   *
   * @param file 头像图片文件
   * @return 头像 URL
   */
  @PutMapping("/avatar")
  @Operation(summary = "上传头像")
  public YdszResponse<String> uploadAvatar(
      @RequestParam("file") MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new com.njydsz.common.exception.custom.BusinessException(
          com.njydsz.userinfo.domain.enums.UserInfoExceptionCode.IMPORT_FILE_EMPTY);
    }

    String userId = RequestContext.getUserId();

    // TODO: 生产环境需调用 common-file 上传文件，获取真实 URL
    // 当前为模拟实现：根据文件名生成模拟 URL
    String avatarUrl = String.format("https://file.ydsz.com/avatar/%s/%s", userId, file.getOriginalFilename());

    // 更新用户头像 URL
    UserProfileUpdateDTO dto = new UserProfileUpdateDTO();
    dto.setAvatar(avatarUrl);
    userAccountService.updateProfile(userId, dto);

    log.info("用户头像上传成功: userId={}, url={}", userId, avatarUrl);
    return YdszResponse.success(avatarUrl);
  }

  // ==================== 双因素认证（MFA）管理 ====================

  /**
   * 查询当前用户是否已启用 MFA。
   *
   * @return true 表示已启用 TOTP 双因素认证
   */
  @GetMapping("/mfa/status")
  @Operation(summary = "查询 MFA 启用状态")
  public YdszResponse<Boolean> getMfaStatus() {
    return YdszResponse.success(mfaService.isMfaEnabled(RequestContext.getUserId()));
  }

  /**
   * 发起 MFA 绑定：生成 TOTP 密钥与 otpauth URI（供二维码扫码）。
   *
   * <p>绑定为两步流程：{@code setup} 返回密钥与二维码 → 用户扫码录入动态码 → {@link #activateMfa}
   * 校验并正式启用。密钥临时保存 5 分钟，超时需重新发起。
   *
   * @return 绑定信息（Base32 密钥 + otpauth URI）
   */
  @PostMapping("/mfa/setup")
  @Operation(summary = "发起 MFA 绑定", description = "返回 TOTP 密钥与 otpauth URI（二维码）")
  public YdszResponse<MfaSetupVO> setupMfa() {
    String userId = RequestContext.getUserId();
    UserAccountVO user = userAccountService.getById(userId);
    return YdszResponse.success(mfaService.setup(userId, user != null ? user.getUsername() : userId));
  }

  /**
   * 激活 MFA 绑定：校验用户录入的动态码后正式启用。
   *
   * @param dto 动态码操作 DTO
   * @return 是否成功
   */
  @PostMapping("/mfa/activate")
  @Operation(summary = "激活 MFA 绑定")
  public YdszResponse<Boolean> activateMfa(@jakarta.validation.Valid @RequestBody MfaOperationDTO dto) {
    mfaService.activate(RequestContext.getUserId(), dto.getCode());
    return YdszResponse.success(true);
  }

  /**
   * 解除 MFA 绑定：校验当前动态码后关闭双因素认证。
   *
   * @param dto 动态码操作 DTO
   * @return 是否成功
   */
  @PostMapping("/mfa/disable")
  @Operation(summary = "解除 MFA 绑定")
  public YdszResponse<Boolean> disableMfa(@jakarta.validation.Valid @RequestBody MfaOperationDTO dto) {
    mfaService.disable(RequestContext.getUserId(), dto.getCode());
    return YdszResponse.success(true);
  }
}
