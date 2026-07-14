package com.njydsz.pmis.common.core.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.njydsz.pmis.common.core.constant.FilterIgnoreConstant;

import lombok.Data;

/**
 * 过滤器忽略路径配置属性。
 *
 * <p>允许通过配置文件覆盖或扩展 {@link FilterIgnoreConstant} 中的默认忽略规则。
 * 未配置时使用内置默认值，配置后与默认值合并。</p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * ydsz:
 *   core:
 *     filter-ignore:
 *       common-ignore-urls:
 *         - /custom/path/**
 *       auth-filter-ignore-service-names:
 *         - ydsz-custom-web
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "ydsz.core.filter-ignore")
public class FilterIgnoreProperties {

    /** 公共忽略 URL 模式列表（与内置默认值合并） */
    private List<String> commonIgnoreUrls = new ArrayList<>();

    /** 认证过滤器忽略服务名称列表（覆盖内置默认值） */
    private List<String> authFilterIgnoreServiceNames = new ArrayList<>();

    /** 安全排除 URL 模式列表（与内置默认值合并） */
    private List<String> securityExcludeUrls = new ArrayList<>();

    /**
     * 获取合并后的公共忽略 URL 集合（配置值 + 内置默认值）。
     *
     * @return 合并后的不可变 URL 模式集合
     */
    public Set<String> getMergedCommonIgnoreUrls() {
        Set<String> merged = new LinkedHashSet<>(FilterIgnoreConstant.getCommonIgnoreUrls());
        merged.addAll(commonIgnoreUrls);
        return merged;
    }

    /**
     * 获取合并后的安全排除 URL 集合（配置值 + 内置默认值）。
     *
     * @return 合并后的不可变 URL 模式集合
     */
    public Set<String> getMergedSecurityExcludeUrls() {
        Set<String> merged = new LinkedHashSet<>(FilterIgnoreConstant.getSecurityExcludeUrls());
        merged.addAll(securityExcludeUrls);
        return merged;
    }

    /**
     * 获取认证过滤器忽略服务名称集合。
     *
     * <p>若配置了自定义值则使用配置值，否则使用内置默认值。</p>
     *
     * @return 服务名称集合
     */
    public Set<String> getResolvedAuthFilterIgnoreServiceNames() {
        if (authFilterIgnoreServiceNames != null && !authFilterIgnoreServiceNames.isEmpty()) {
            return new LinkedHashSet<>(authFilterIgnoreServiceNames);
        }
        return FilterIgnoreConstant.getAuthFilterIgnoreServiceNames();
    }
}
