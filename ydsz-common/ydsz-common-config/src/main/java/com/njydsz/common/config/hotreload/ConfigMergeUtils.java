package com.njydsz.common.config.hotreload;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.json.tree.JsonNode;

/**
 * 多层配置合并工具（基于 RFC 7396 JSON Merge Patch 语义）。
 *
 * <p>提供 JSON 配置的深度合并能力，用于以下标准化场景：
 *
 * <ul>
 *   <li>基础配置 + 租户覆盖配置 → 最终生效配置（{@code TenantConfigProvider} 未来增强路径）
 *   <li>默认配置 + 环境覆盖配置 → 运行时配置（多环境配置层叠）
 *   <li>Nacos 远程配置 + 本地 override → 合并配置（远程 + 本地优先级仲裁）
 * </ul>
 *
 * <p><b>合并规则（RFC 7396）：</b>
 *
 * <ul>
 *   <li>patch 中的字段覆盖 target 中的同名字段
 *   <li>patch 中值为 {@code null} 的字段从 target 中删除
 *   <li>patch 中不存在的字段保留 target 中的原值
 *   <li>嵌套对象递归合并（非整体替换）
 * </ul>
 *
 * <p>核心算法委托给 {@link YdszJson#applyMergePatch(JsonNode, JsonNode)}，本类提供 String 入参出参的便捷封装。
 *
 * <p><b>储备说明：</b>当前项目暂未落地多层 JSON 配置合并场景（Spring Boot {@code Environment}
 * 属性源优先级体系已覆盖扁平 key-value 场景）。本工具为未来 tenant-override / feature-flag /
 * config-profile 等深层 JSON 合并场景预置，后续相关需求<b>必须</b>使用本类，禁止自行实现 JSON 深
 * 度合并逻辑。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * String baseConfig = "{\"timeout\":30,\"retry\":3,\"pool\":{\"min\":1,\"max\":10}}";
 * String override   = "{\"retry\":5,\"pool\":{\"max\":20}}";
 * String merged = ConfigMergeUtils.merge(baseConfig, override);
 * // 结果: {"timeout":30,"retry":5,"pool":{"min":1,"max":20}}
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.10.01
 * @see com.njydsz.common.config.ConfigAutoConfiguration
 * @see com.njydsz.common.config.hotreload.ConfigChangeBridge
 */
public final class ConfigMergeUtils {

  private static final Logger LOG = LoggerFactory.getLogger(ConfigMergeUtils.class);

  private ConfigMergeUtils() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * 合并两个 JSON 配置。
   *
   * @param baseConfig 基础配置 JSON 字符串
   * @param overrideConfig 覆盖配置 JSON 字符串（优先级更高）
   * @return 合并后的 JSON 字符串；如果 override 为空返回 base；如果 base 为空返回 override
   */
  public static String merge(String baseConfig, String overrideConfig) {
    if (baseConfig == null || baseConfig.isBlank()) {
      return overrideConfig;
    }
    if (overrideConfig == null || overrideConfig.isBlank()) {
      return baseConfig;
    }
    try {
      JsonNode base = YdszJson.readTree(baseConfig);
      JsonNode patch = YdszJson.readTree(overrideConfig);
      JsonNode merged = YdszJson.applyMergePatch(base, patch);
      String result = merged.toString();
      LOG.debug("[ConfigMerge] 配置合并完成: base keys={} → merged", baseConfig.length());
      return result;
    } catch (Exception e) {
      LOG.warn("[ConfigMerge] 配置合并失败，降级返回 override: {}", e.getMessage());
      return overrideConfig;
    }
  }

  /**
   * 多层配置合并（按优先级从低到高）。
   *
   * <p>例如：{@code mergeLayers(defaults, envConfig, tenantConfig)} 后者覆盖前者。
   *
   * @param configs 按优先级从低到高排列的配置 JSON 数组
   * @return 合并后的 JSON 字符串
   */
  public static String mergeLayers(String... configs) {
    if (configs == null || configs.length == 0) {
      return null;
    }
    String result = configs[0];
    for (int i = 1; i < configs.length; i++) {
      result = merge(result, configs[i]);
    }
    return result;
  }
}
