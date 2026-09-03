package com.njydsz.common.web.config;
import java.util.LinkedHashSet;
import java.util.Set;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import com.njydsz.common.auth.constant.FilterIgnoreConstants;

/**
 * 过滤器忽略路径配置属性。
 *
 * <p>允许通过配置文件覆盖或扩展 {@link FilterIgnoreConstants} 中的默认忽略规则。 默认与内置默认值<b>合并</b>；设置 {@link
 * #isReplaceBuiltin()} 为 {@code true} 时改为<b>整体替换</b>内置默认值。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * ydsz:
 *   core:
 *     filter-ignore:
 *       common-ignore-urls:
 *         - /custom/path/**
 *       auth-filter-ignore-service-names:
 *         - ydsz-custom-web
 *       replace-builtin: false   # 默认 false：合并；true：替换
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@ConfigurationProperties(prefix = "ydsz.core.filter-ignore")
public class FilterIgnoreProperties {

  /** 公共忽略 URL 模式列表（与内置默认值合并） */
  private Set<String> commonIgnoreUrls = new LinkedHashSet<>(4);

  /** 认证过滤器忽略的服务名称列表（与内置默认值合并） */
  private Set<String> authFilterIgnoreServiceNames = new LinkedHashSet<>(4);

  /**
   * 是否整体替换内置默认值（默认 false：合并；true：替换）。
   *
   * <p>为 true 时，{@link #commonIgnoreUrls} 和 {@link #authFilterIgnoreServiceNames} 将完全覆盖 {@link
   * FilterIgnoreConstants} 中的内置默认值，而非与之合并。
   */
  private boolean replaceBuiltin = false;

  /**
   * 获取有效的公共忽略 URL 集合。
   *
   * <p>当 {@link #replaceBuiltin} 为 {@code false} 时，与 {@link FilterIgnoreConstants#getCommonIgnoreUrls()} 合并； 为 {@code true}
   * 时，仅使用本配置值。
   *
   * @return 有效的 URL 集合（不可变）
   */
  public Set<String> getResolvedCommonIgnoreUrls() {
    if (replaceBuiltin) {
      return Set.copyOf(commonIgnoreUrls);
    }
    Set<String> merged = new LinkedHashSet<>(FilterIgnoreConstants.getCommonIgnoreUrls());
    merged.addAll(commonIgnoreUrls);
    return merged;
  }

  /**
   * 获取有效的认证过滤器忽略服务名称集合。
   *
   * <p>当 {@link #replaceBuiltin} 为 {@code false} 时，与 {@link
   * FilterIgnoreConstants#getAuthFilterIgnoreServiceNames()} 合并； 为 {@code true} 时，仅使用本配置值。
   *
   * @return 有效的服务名称集合（不可变）
   */
  public Set<String> getResolvedAuthFilterIgnoreServiceNames() {
    if (replaceBuiltin) {
      return Set.copyOf(authFilterIgnoreServiceNames);
    }
    Set<String> merged = new LinkedHashSet<>(FilterIgnoreConstants.getAuthFilterIgnoreServiceNames());
    merged.addAll(authFilterIgnoreServiceNames);
    return merged;
  }
}
