package com.njydsz.userinfo.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.util.SecurityUtils;
import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.userinfo.domain.dto.UserPreferenceDTO;
import com.njydsz.userinfo.domain.vo.UserPreferenceVO;
import com.njydsz.userinfo.server.service.UserPreferenceService;

/**
 * 用户偏好 Controller
 *
 * <p>提供当前登录用户偏好（默认首页/语言/主题/布局等）的读取、保存与重置端点，
 * 供前端「用户偏好设置」与「个性化首屏」能力使用。数据按用户隔离。
 *
 * <p><b>接口路径：</b>{@code /api/user/preferences}
 *
 * <p><b>安全特性：</b>
 *
 * <ul>
 *   <li>需登录态（由网关透传认证上下文，未登录返回 401）
 *   <li>仅允许读写当前登录用户自己的偏好（userId 取自认证上下文，不可指定他人）
 *   <li>读接口限流 100 QPS，写接口限流 20 QPS
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.08
 * @see com.njydsz.userinfo.server.service.UserPreferenceService 偏好业务逻辑
 * @see com.njydsz.userinfo.domain.vo.UserPreferenceVO 偏好 VO
 */
@Slf4j
@ApiVersion("26.09.01")
@RestController
@RequestMapping("/api/user/preferences")
@RequiredArgsConstructor
@Tag(name = "用户偏好", description = "当前用户偏好的读取/保存/重置")
public class UserPreferenceController {

  private final UserPreferenceService service;

  /**
   * 查询当前用户偏好配置。
   *
   * <p>用户从未保存过偏好时返回全空字段 VO，由前端回退本地默认值。
   *
   * @return 用户偏好 VO
   */
  @RateLimit(resource = "userinfo.UserPreference.get", threshold = 100)
  @GetMapping
  @Operation(summary = "查询当前用户偏好配置")
  public YdszResponse<UserPreferenceVO> get() {
    return YdszResponse.success(service.get(SecurityUtils.getCurrentUserId()));
  }

  /**
   * 保存当前用户偏好配置（PUT 语义：整体覆盖）。
   *
   * <p>限流 20 QPS：偏好保存由前端防抖触发（变更后延迟写入），无需高频。
   *
   * @param dto 偏好配置（对齐前端 UserPreferenceDTO 契约）
   * @return 是否保存成功
   */
  @RateLimit(resource = "userinfo.UserPreference.save", threshold = 20)
  @PutMapping
  @Operation(summary = "保存当前用户偏好配置")
  public YdszResponse<Boolean> save(@Valid @RequestBody UserPreferenceDTO dto) {
    return YdszResponse.success(service.save(SecurityUtils.getCurrentUserId(), dto));
  }

  /**
   * 重置当前用户偏好为默认值。
   *
   * <p>删除已持久化的偏好数据，返回全空字段 VO（前端按缺省字段回退默认值）。
   *
   * @return 重置后的偏好 VO（全空字段）
   */
  @RateLimit(resource = "userinfo.UserPreference.reset", threshold = 20)
  @PostMapping("/reset")
  @Operation(summary = "重置当前用户偏好为默认值")
  public YdszResponse<UserPreferenceVO> reset() {
    return YdszResponse.success(service.reset(SecurityUtils.getCurrentUserId()));
  }
}
