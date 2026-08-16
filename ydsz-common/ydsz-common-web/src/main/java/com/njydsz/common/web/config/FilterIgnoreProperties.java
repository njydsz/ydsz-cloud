package com.njydsz.common.web.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import com.njydsz.common.auth.config.AuthFilterIgnoreProperties;
import com.njydsz.common.auth.constant.FilterIgnoreConstants;

/**
 * 过滤器忽略路径配置属性。
 *
 * <p>允许通过配置文件覆盖或扩展 {@link FilterIgnoreConstants} 中的默认忽略规则。
 * 默认与内置默认值<b>合并</b>；设置 {@link #isReplaceBuiltin()} 为 {@code true}
 * 时改为<b>整体替换</b>内置默认值。</p>
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
 *       replace-builtin: false   # 默认 false：合并；true：替换
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "ydsz.core.filter-ignore")
public class FilterIgnoreProperties {

    /** 公共忽略 URL 模式列表（与内置默认值合并） */
    private List<String> commonIgnoreUrls = new ArrayList<>();

    /** 认证过滤器忽略服务名称列表（与内置默认值合并） */
    private List<String> authFilterIgnoreServiceNames = new ArrayList<>();

    /** 安全排除 URL 模式列表（与内置默认值合并） */
    private List<String> securityExcludeUrls = new ArrayList<>();

    /**
     * 是否用配置值整体替换内置默认值。
     *
     * <p>{@code false}（默认）：配置值与内置默认值合并；
     * {@code true}：仅使用配置值，忽略内置默认值。</p>
     */
    private boolean replaceBuiltin = false;

    /**
     * 获取合并后的公共忽略 URL 集合。
     *
     * <p>{@code replaceBuiltin=true} 时仅返回配置值。</p>
     *
     * @return 合并后的不可变 URL 模式集合
     */
    public Set<String> getMergedCommonIgnoreUrls() {
        if (replaceBuiltin) {
            return new LinkedHashSet<>(commonIgnoreUrls);
        }
        Set<String> merged = new LinkedHashSet<>(FilterIgnoreConstants.getCommonIgnoreUrls());
        merged.addAll(commonIgnoreUrls);
        return merged;
    }

    /**
     * 获取合并后的安全排除 URL 集合。
     *
     * <p>{@code replaceBuiltin=true} 时仅返回配置值。</p>
     *
     * @return 合并后的不可变 URL 模式集合
     */
    public Set<String> getMergedSecurityExcludeUrls() {
        if (replaceBuiltin) {
            return new LinkedHashSet<>(securityExcludeUrls);
        }
        Set<String> merged = new LinkedHashSet<>(FilterIgnoreConstants.getSecurityExcludeUrls());
        merged.addAll(securityExcludeUrls);
        return merged;
    }

    /**
     * 获取认证过滤器忽略服务名称集合。
     *
     * <p>默认与内置默认值合并；{@code replaceBuiltin=true} 时仅使用配置值。
     * 若配置值非空，无论开关如何，均以配置值为主（合并时内置值打底）。</p>
     *
     * @return 服务名称集合
     */
    public Set<String> getResolvedAuthFilterIgnoreServiceNames() {
        Set<String> result = new LinkedHashSet<>();
        if (!replaceBuiltin) {
            result.addAll(FilterIgnoreConstants.getAuthFilterIgnoreServiceNames());
        }
        result.addAll(authFilterIgnoreServiceNames);
        return result;
    }
}
