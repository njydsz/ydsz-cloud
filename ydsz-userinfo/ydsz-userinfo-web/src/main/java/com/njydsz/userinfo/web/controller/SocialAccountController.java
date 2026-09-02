package com.njydsz.userinfo.web.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.userinfo.domain.config.SocialAuthProperties;
import com.njydsz.userinfo.domain.vo.SocialAccountVO;
import com.njydsz.userinfo.server.auth.SocialAuthService;

/**
 * 第三方账号绑定管理 Controller（用户中心）。
 *
 * <p>提供当前登录用户的社交账号绑定管理能力，包括：
 *
 * <ul>
 *   <li>查询已绑定的社交账号列表</li>
 *   <li>查询可用的第三方登录平台及其绑定状态</li>
 *   <li>解绑指定的社交账号</li>
 * </ul>
 *
 * <p><b>接口路径：</b>{@code /api/v1/profile/social}
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/profile/social")
@RequiredArgsConstructor
@Tag(name = "第三方账号绑定", description = "社交账号绑定管理")
public class SocialAccountController {

  private final SocialAuthService socialAuthService;
  private final SocialAuthProperties socialAuthProperties;

  /**
   * 获取当前用户已绑定的社交账号列表。
   *
   * @return 社交账号绑定列表
   */
  @GetMapping("/bindings")
  @Operation(summary = "获取已绑定的社交账号列表")
  public YdszResponse<List<SocialAccountVO>> getBindings() {
    String userId = RequestContext.getUserId();
    return YdszResponse.success(socialAuthService.listBindings(userId));
  }

  /**
   * 获取可用的第三方登录平台列表及其绑定状态。
   *
   * <p>返回所有已配置的平台信息，并标注当前用户是否已绑定各平台。
   * 前端可根据此列表展示「绑定/解绑」按钮。
   *
   * @return 平台列表（含绑定状态）
   */
  @GetMapping("/platforms")
  @Operation(summary = "获取可用的第三方平台及绑定状态")
  public YdszResponse<List<PlatformBindingStatusVO>> getAvailablePlatforms() {
    String userId = RequestContext.getUserId();
    List<SocialAccountVO> bindings = socialAuthService.listBindings(userId);

    // 获取已绑定平台集合
    Map<String, SocialAccountVO> bindingMap = bindings.stream()
        .collect(Collectors.toMap(
            vo -> vo.getPlatform().toLowerCase(),
            vo -> vo,
            (existing, replacement) -> existing));

    // 构建平台列表
    List<PlatformBindingStatusVO> platforms = socialAuthProperties.getProviders().entrySet().stream()
        .map(entry -> {
          String platformKey = entry.getKey().toLowerCase();
          SocialAuthProperties.ProviderConfig config = entry.getValue();
          SocialAccountVO binding = bindingMap.get(platformKey);

          return new PlatformBindingStatusVO(
              platformKey,
              getPlatformDisplayName(platformKey),
              config.getAppId(),
              binding != null,
              binding != null ? binding.getCreatedAt() : null,
              binding != null ? binding.getNickname() : null);
        })
        .toList();

    return YdszResponse.success(platforms);
  }

  /**
   * 解绑当前用户的指定社交账号。
   *
   * @param platform 平台标识（如 WECHAT/DINGTALK/FEISHU）
   * @return 是否成功
   */
  @DeleteMapping("/bindings/{platform}")
  @Operation(summary = "解绑指定的社交账号")
  public YdszResponse<Boolean> unbind(@PathVariable String platform) {
    String userId = RequestContext.getUserId();
    socialAuthService.unbind(userId, platform);
    return YdszResponse.success(true);
  }

  /**
   * 获取平台显示名称。
   *
   * @param platform 平台标识
   * @return 显示名称
   */
  private String getPlatformDisplayName(String platform) {
    return switch (platform.toUpperCase()) {
      case "ENTERPRISE_WECHAT" -> "企业微信";
case "DINGTALK" -> "IM";
case "FEISHU" -> "IM";
      case "GITHUB" -> "GitHub";
      case "GOOGLE" -> "Google";
      case "WECHAT" -> "微信";
      default -> platform;
    };
  }

  /**
   * 平台绑定状态 VO。
   *
   * <p>返回前端展示第三方平台及其绑定状态。
   *
   * @param platform 平台标识
   * @param platformName 平台显示名称
   * @param appId 平台应用 ID（脱敏展示）
   * @param bound 是否已绑定
   * @param boundAt 绑定时间
   * @param nickname 社交平台昵称
   * @author ydsz-team
   * @since 26.09.01
   */
  public record PlatformBindingStatusVO(
      String platform,
      String platformName,
      String appId,
      boolean bound,
      LocalDateTime boundAt,
      String nickname) {
  }
}
