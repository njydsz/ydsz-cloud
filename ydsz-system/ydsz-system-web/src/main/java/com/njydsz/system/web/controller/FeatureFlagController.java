package com.njydsz.system.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.system.server.service.ConfigService;

import java.util.Map;

/**
 * 远程特性开关 Controller。
 *
 * <p>为前端 {@code FeatureFlagsManager} 的远程开关加载器（{@code GET /api/feature-flags/me}）
 * 提供数据源。前端在应用引导阶段调用本端点，加载当前用户可见的开关映射；
 * 加载失败时前端自动降级为本地默认值（{@code remoteLoader} 内部捕获），不阻塞启动。
 *
 * <p><b>接口路径：</b>{@code /api/feature-flags}
 *
 * <p>特性开关的过滤与值解析逻辑已下沉到 {@link ConfigService#getFeatureFlags()}，
 * 本 Controller 仅作为 HTTP 适配层委托调用。
 *
 * <p><b>限流：</b>20 QPS（仅应用引导阶段调用，ConfigService 内部有 Caffeine 一级缓存）。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see ConfigService#getFeatureFlags 特性开关过滤与值解析
 */
@Slf4j
@ApiVersion("26.09.01")
@RestController
@RequestMapping("/feature-flags")
@RequiredArgsConstructor
@Tag(name = "远程特性开关", description = "前端 FeatureFlagsManager 远程开关数据源")
public class FeatureFlagController {

  private final ConfigService configService;

  /**
   * 查询当前用户可见的远程特性开关映射。
   *
   * <p>委托 {@link ConfigService#getFeatureFlags()} 按约定过滤公开配置并解析开关值，
   * 返回开关名 → 开关值（布尔/数值/字符串）的映射。
   *
   * @return 开关名 → 开关值的映射（无开关配置时返回空 Map，前端回退默认值）
   */
  @RateLimit(resource = "system.FeatureFlag.me", threshold = 20)
  @GetMapping("/me")
  @Operation(summary = "查询当前用户可见的远程特性开关", description = "按约定过滤公开配置，返回开关名到解析值（布尔/数值/字符串）的映射")
  public YdszResponse<Map<String, Object>> me() {
    return YdszResponse.success(configService.getFeatureFlags());
  }
}
