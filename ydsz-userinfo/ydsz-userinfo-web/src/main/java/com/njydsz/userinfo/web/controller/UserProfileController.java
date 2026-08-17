package com.njydsz.userinfo.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.userinfo.domain.dto.UserProfileUpdateDTO;
import com.njydsz.userinfo.domain.entity.UserAccount;
import com.njydsz.userinfo.domain.vo.UserAccountVO;
import com.njydsz.userinfo.infra.mapper.UserAccountMapper;

/**
 * 用户资料 Controller（个人中心）。
 *
 * <p>提供当前登录用户的个人资料管理能力，包括：查看资料、修改基本信息、更新头像。
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

  private final UserAccountMapper userAccountMapper;

  /**
   * 获取当前登录用户的个人资料。
   *
   * @return 用户资料 VO
   */
  @GetMapping("/me")
  @Operation(summary = "获取当前用户资料")
  public BaseResponse<UserAccountVO> getCurrentUserProfile() {
    String userId = RequestContext.getUserId();
    UserAccount user = userAccountMapper.selectById(userId);
    if (user == null) {
      return BaseResponse.success(null);
    }
    return BaseResponse.success(UserAccountVO.fromEntity(user));
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
  public BaseResponse<Boolean> updateCurrentUserProfile(@RequestBody UserProfileUpdateDTO dto) {
    String userId = RequestContext.getUserId();
    UserAccount user = userAccountMapper.selectById(userId);
    if (user == null) {
      return BaseResponse.success(false);
    }

    // 仅更新非空字段
    if (dto.getRealName() != null) {
      user.setRealName(dto.getRealName());
    }
    if (dto.getPhone() != null) {
      user.setPhone(dto.getPhone());
    }
    if (dto.getEmail() != null) {
      user.setEmail(dto.getEmail());
    }
    if (dto.getAvatar() != null) {
      user.setAvatar(dto.getAvatar());
    }

    userAccountMapper.updateById(user);
    log.info("用户资料更新成功: userId={}", userId);
    return BaseResponse.success(true);
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
  public BaseResponse<String> uploadAvatar(
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
    UserAccount user = userAccountMapper.selectById(userId);
    if (user != null) {
      user.setAvatar(avatarUrl);
      userAccountMapper.updateById(user);
    }

    log.info("用户头像上传成功: userId={}, url={}", userId, avatarUrl);
    return BaseResponse.success(avatarUrl);
  }
}
