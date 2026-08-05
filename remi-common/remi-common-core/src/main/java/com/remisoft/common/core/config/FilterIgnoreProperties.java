package com.remisoft.common.core.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.remisoft.common.core.constant.FilterIgnoreConstant;

/**
 * 过滤器忽略配置属性。
 *
 * <p>允许应用通过 {@code remi.core.filter-ignore.auth-filter-ignore-service-names} 配置项，
 * 在运行时动态追加或覆盖认证过滤器忽略的服务名称，避免新增/移除 web 模块时修改 core 模块代码。</p>
 *
 * <p><b>默认值：</b>包含项目内置的 10 个 web 模块名，可通过配置覆盖。
 * 当配置项{@link #isOverrideMode() overrideMode} 为 {@code false}（默认）,
 * 配置的列表将与默认值合并;当 overrideMode 为 {@code true} 时,将完全替换默认集合。</p>
 *
 * <p><b>设计理念:</b> 取代 {@link FilterIgnoreConstant} 中硬编码服务名的做法，
 * 将默认值内聚到配置属性中，新增/移除 web 模块时仅需修改配置文件。</p>
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
     * 默认的认证过滤器忽略服务名称列表。
     *
     * <p>当用户未配置 {@code auth-filter-ignore-service-names} 时，使用此默认值。
     * 包含项目内置的 web 模块名。</p>
     */
    private static final List<String> DEFAULT_IGNORE_SERVICE_NAMES = Arrays.asList(
            "remi-gateway",
            "remi-system-web",
            "remi-userinfo-web",
            "remi-message-web",
            "remi-cronjob-web",
            "remi-agent-web",
            "remi-nextwiki-web",
            "remi-literule-web",
            "remi-workflow-web",
            "remi-project-web"
    );

    /**
     * 是否使用"替换模式"替代"合并模式"。
     *
     * <ul>
     *   <li>{@code false} (默认): 将配置的列表与默认值合并</li>
     *   <li>{@code true}: 完全替换默认集合,仅保留配置的列表</li>
     * </ul>
     */
    private boolean overrideMode = false;

    /**
     * 认证过滤器忽略的服务名称列表。
     *
     * <p>通过此配置,可在不修改 core 模块代码的前提下,从外部追加或覆盖需要跳过认证过滤的 web 模块名。
     * 结合 {@link #isOverrideMode()} 控制合并或替换语义。
     * 未配置时使用 {@value #DEFAULT_IGNORE_SERVICE_NAMES} 中的默认值。</p>
     */
    private List<String> authFilterIgnoreServiceNames = new ArrayList<>(DEFAULT_IGNORE_SERVICE_NAMES);

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
                : new ArrayList<>(DEFAULT_IGNORE_SERVICE_NAMES);
    }

    /**
     * 解析并返回最终使用的服务名称集合。
     *
     * <p>始终使用当前配置的默认值或用户自定义值，不再依赖 {@link FilterIgnoreConstant} 的硬编码集合。</p>
     *
     * @return 当前配置的服务名称集合(不可变);永不为 {@code null}
     */
    public java.util.Set<String> getResolvedAuthFilterIgnoreServiceNames() {
        return java.util.Set.copyOf(authFilterIgnoreServiceNames);
    }
}
