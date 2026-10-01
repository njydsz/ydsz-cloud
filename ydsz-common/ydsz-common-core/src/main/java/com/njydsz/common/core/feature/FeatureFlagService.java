package com.njydsz.common.core.feature;

import java.util.Collections;
import java.util.Map;

/**
 * 特性开关（Feature Flag）服务接口。
 *
 * <p>提供统一的特性开关查询能力，用于灰度发布、渐进式上线、紧急熔断等场景。 特性开关由配置驱动（{@code ydsz.core.feature-flags.*}），
 * 支持运行期动态刷新（Spring 配置刷新机制），业务代码通过
 * {@link FeatureFlagContext#isEnabled(String)} 静态门面或注入本接口访问。
 *
 * <p><b>设计原则：</b>
 *
 * <ul>
 *   <li>开关未配置时返回默认值（默认开启），保证新增开关不阻塞业务
 *   <li>开关名称使用小写点分格式（如 {@code user.register.sms}），禁止魔法值散落业务代码
 *   <li>紧急关闭场景可配置 {@code false} 立即熔断，无需发版
 * </ul>
 *
 * @author ydsz-team
 * @since 26.10.01
 * @see FeatureFlagContext
 */
public interface FeatureFlagService {

  /**
   * 查询特性开关是否开启。
   *
   * @param name 开关名称（小写点分格式）
   * @return 开启返回 true；未配置时返回 true（默认开启）
   */
  boolean isEnabled(String name);

  /**
   * 查询特性开关是否开启，并指定未配置时的默认值。
   *
   * @param name 开关名称（小写点分格式）
   * @param defaultValue 未配置时的默认值
   * @return 开启返回 true；未配置时返回 {@code defaultValue}
   */
  boolean isEnabled(String name, boolean defaultValue);

  /**
   * 获取当前用户/租户可见的全部特性开关映射。
   *
   * <p>用于前端启动时一次性加载所有开关状态。默认实现返回空映射，子类按需重写。
   *
   * @return 开关名 → 开关值（布尔/数值/字符串）的映射；无开关时返回空 Map
   * @since 26.09.30
   */
  default Map<String, Object> getFeatureFlags() {
    return Collections.emptyMap();
  }
}
