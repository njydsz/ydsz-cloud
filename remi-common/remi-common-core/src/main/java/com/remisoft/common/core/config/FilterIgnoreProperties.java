package com.remisoft.common.core.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.remisoft.common.core.constant.FilterIgnoreConstant;

/**
 * 过滤器忽略配置属性。
 *
 * <p>允许应用通过 {@code remi.core.filter-ignore.auth-filter-ignore-service-names} 配置项，
 * 在运行时动态追加或覆盖认证过滤器忽略的服务名称，避免新增/移除 web 模块时修改 core 模块代码。</p>
 *
 * <p><b>默认值：</b>取自 {@link FilterIgnoreConstant#AUTH_FILTER_IGNORE_SERVICE_NAME} 硬编码集合。
 * 当配置项{@link #isOverrideMode() overrideMode} 为 {@code false}（默认）,
 * 配置的列表将与默认值合并;当 overrideMode 为 {@code true} 时,将完全替换默认集合。</p>
 *
 * <p><b>设计理念:</b> {@code 取代直接暴露不可变常量集合的做法,通过 Spring 属性绑定,
 * 在 {@link FilterIgnoreConstant} 的静态回退值和外部配置之间加一层可观测、可覆盖的适配器。}</p>
 *
 * <p><b>使用示例:</b></p>
 * <pre>{@code
 * remi:
 *   core:
 *     filter-ignore:
 *       auth-filter-ignore-service-names:
 *         - remi-invoice-web
 *         - remi-report-web
 *       override-mode: false
 * }</pre>
 *
 * @author remi-team
 * @since 1.7.0
 * @see FilterIgnoreConstant
 */
@ConfigurationProperties(prefix = "remi.core.filter-ignore")
public class FilterIgnoreProperties {

    /**
     * 是否使用"替换模式"替代"合并模式"。
     *
     * <ul>
     *   <li>{@code false} (默认): 将配置的列表与 {@link FilterIgnoreConstant} 硬编码默认值合并</li>
     *   <li>{@code true}: 完全替换默认集合,仅保留配置的列表</li>
     * </ul>
     */
    private boolean overrideMode = false;

    /**
     * 认证过滤器忽略的服务名称列表。
     *
     * <p>通过此配置,可在不修改 core 模块代码的前提下,从外部追加或覆盖需要跳过认证过滤的 web 模块名。
     * 结合 {@link #isOverrideMode()} 控制合并或替换语义。</p>
     */
    private List<String> authFilterIgnoreServiceNames = new ArrayList<>();

    public boolean isOverrideMode() {
        return overrideMode;
    }

    public void setOverrideMode(boolean overrideMode) {
        this.overrideMode = overrideMode;
    }

    public List<String> getAuthFilterIgnoreServiceNames() {
        return authFilterIgnoreServiceNames;
    }

    public void setAuthFilterIgnoreServiceNames(List<String> authFilterIgnoreServiceNames) {
        this.authFilterIgnoreServiceNames = authFilterIgnoreServiceNames != null
                ? authFilterIgnoreServiceNames
                : new ArrayList<>();
    }

    /**
     * 解析并返回最终使用的服务名称集合。
     *
     * <ul>
     *   <li>若 {@link #isOverrideMode()} 为 {@code true},仅返回配置的列表</li>
     *   <li>否则与 {@link FilterIgnoreConstant#getAuthFilterIgnoreServiceNames()} 合并</li>
     * </ul>
     *
     * @return 合并或替换后的服务名称集合(不可变);永不为 {@code null}
     */
    public java.util.Set<String> getResolvedAuthFilterIgnoreServiceNames() {
        if (overrideMode) {
            return java.util.Set.copyOf(authFilterIgnoreServiceNames);
        }
        java.util.Set<String> merged = new java.util.LinkedHashSet<>(
                FilterIgnoreConstant.getAuthFilterIgnoreServiceNames());
        merged.addAll(authFilterIgnoreServiceNames);
        return java.util.Set.copyOf(merged);
    }
}
