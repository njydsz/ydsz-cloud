package com.njydsz.common.web.config;.config
import java.util.LinkedHashSet;
import java.util.List;
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
  private List<String> commonIgnoreUrls = new ArrayList<>(4);