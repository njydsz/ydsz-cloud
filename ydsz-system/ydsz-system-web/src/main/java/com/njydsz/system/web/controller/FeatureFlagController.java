package com.njydsz.system.web.controller;

import java.util.HashMap;
import java.util.Map;

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
import com.njydsz.system.domain.vo.ConfigVO;
import com.njydsz.system.server.service.ConfigService;

/**
 * 远程特性开关 Controller
 *
 * <p>为前端 {@code FeatureFlagsManager} 的远程开关加载器（{@code GET /api/feature-flags/me}）
 * 提供数据源。前端在应用引导阶段调用本端点，加载当前用户可见的开关映射；
 * 加载失败时前端自动降级为本地默认值（{@code remoteLoader} 内部捕获），不阻塞启动。
 *
 * <p><b>接口路径：</b>{@code /api/feature-flags}
 *
 * <p><b>开关配置约定：</b>开关项以系统配置（{@code ConfigService.listPublicConfigs}）承载，
 * 满足以下任一条件的公开配置视为特性开关：
 *
 * <ul>
 *   <li>配置分组 {@code configGroup == "feature-flag"}
 *   <li>配置键以 {@code feature.} 为前缀（下发时剔除前缀作为开关名）
 * </ul>
 *
 * <p>开关值按内容解析：{@code true/false} → 布尔；整数/小数 → 数值；其余 → 字符串。
 *
 * @author ydsz-team
 * @since 26.09.08
 * @see ConfigService 公开配置查询
 */
@Slf4j
@ApiVersion("26.09.01")
@RestController
@RequestMapping("/api/feature-flags")
@RequiredArgsConstructor
@Tag(name = "远程特性开关", description = "前端 FeatureFlagsManager 远程开关数据源")
public class FeatureFlagController {

  /** 特性开关配置分组约定值 */
  private static final String CONFIG_GROUP_FEATURE_FLAG = "feature-flag";

  /** 特性开关配置键前缀约定值 */
  private static final String CONFIG_KEY_PREFIX = "feature.";

  /** 前缀长度（用于剔除前缀得到开关名） */
  private static final int CONFIG_KEY_PREFIX_LENGTH = CONFIG_KEY_PREFIX.length();

  /** Map 初始容量 */
  private static final int MAP_CAPACITY = 16;

  private final ConfigService configService;

  /**
   * 查询当前用户可见的远程特性开关映射。
   *
   * <p>读取全部公开配置并按约定过滤出开关项（见类文档「开关配置约定」），
   * 键为开关名（剔除 {@code feature.} 前缀），值为按内容解析后的布尔/数值/字符串。
   *
   * <p>限流 20 QPS：仅应用引导阶段调用，ConfigService 内部有 Caffeine 一级缓存。
   *
   * @return 开关名 → 开关值的映射（无开关配置时返回空 Map，前端回退默认值）
   */
  @RateLimit(resource = "system.FeatureFlag.me", threshold = 20)
  @GetMapping("/me")
  @Operation(summary = "查询当前用户可见的远程特性开关", description = "按约定过滤公开配置，返回开关名到解析值（布尔/数值/字符串）的映射")
  public YdszResponse<Map<String, Object>> me() {
    Map<String, Object> flags = new HashMap<>(MAP_CAPACITY);
    for (ConfigVO config : configService.listPublicConfigs()) {
      String flagKey = extractFlagKey(config);
      if (flagKey == null) {
        continue;
      }
      flags.put(flagKey, parseFlagValue(config.getConfigValue()));
    }
    return YdszResponse.success(flags);
  }

  /**
   * 提取开关名：非开关配置返回 {@code null}，前缀键剔除前缀。
   *
   * @param config 公开配置项
   * @return 开关名；非开关配置返回 {@code null}
   */
  private String extractFlagKey(ConfigVO config) {
    if (config == null || config.getConfigKey() == null) {
      return null;
    }
    if (CONFIG_GROUP_FEATURE_FLAG.equals(config.getConfigGroup())) {
      return config.getConfigKey();
    }
    if (config.getConfigKey().startsWith(CONFIG_KEY_PREFIX)) {
      return config.getConfigKey().substring(CONFIG_KEY_PREFIX_LENGTH);
    }
    return null;
  }

  /**
   * 按内容解析开关值：布尔 → 整数 → 小数 → 字符串。
   *
   * @param raw 配置原始值
   * @return 解析后的开关值；原始值为空时返回空字符串
   */
  private Object parseFlagValue(String raw) {
    if (raw == null || raw.isBlank()) {
      return "";
    }
    String value = raw.trim();
    if ("true".equalsIgnoreCase(value)) {
      return Boolean.TRUE;
    }
    if ("false".equalsIgnoreCase(value)) {
      return Boolean.FALSE;
    }
    if (value.matches("-?\\d+")) {
      return Long.parseLong(value);
    }
    if (value.matches("-?\\d+\\.\\d+")) {
      return Double.parseDouble(value);
    }
    return value;
  }
}
