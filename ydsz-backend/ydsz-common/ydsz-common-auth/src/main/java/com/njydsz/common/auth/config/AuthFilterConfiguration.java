package com.njydsz.common.auth.config;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.njydsz.common.domain.config.FilterIgnoreConstant;
import com.njydsz.common.util.url.UrlPathUtils;

/**
 * 认证过滤器配置。
 *
 * <p>管理 WebAuthFilter 的核心配置：跳过路径白名单、Token 解析器、Header 名称、用户上下文写入策略。
 *
 * <p>WebAuthFilter 通过该配置决定哪些请求放行、哪些需要校验 Token。
 *
 * @author ydsz-team
 * @since 1.0.0
 */

@AutoConfiguration
@EnableConfigurationProperties(AuthFilterProperties.class)
public class AuthFilterConfiguration {

    private final AuthFilterProperties properties;

    public AuthFilterConfiguration(AuthFilterProperties properties) {
        this.properties = properties;
    }

    /**
     * 获取应用级通用放行路径列表。
     *
     * @return 应用级通用忽略路径；未配置时返回空列表，被显式置 {@code null} 时可能返回 {@code null}
     */
    public List<String> getCommonIgnoreUrl() {
        return properties.getCommonIgnoreUrl();
    }

    /**
     * 设置应用级通用放行路径。
     *
     * <p>匹配的请求在 WebAuthFilter 中跳过 Token 与权限校验，通常用于健康检查、静态资源、内部探针等无需鉴权的端点。
     * 传入 {@code null} 会清空原配置，调用方需确保非空，避免误放行业务接口。</p>
     *
     * @param commonIgnoreUrl 应用级通用忽略路径列表，允许为 {@code null}
     */
    public void setCommonIgnoreUrl(List<String> commonIgnoreUrl) {
        properties.setCommonIgnoreUrl(commonIgnoreUrl);
    }

    /**
     * 获取网关层放行路径列表。
     *
     * @return 网关级忽略路径；未配置时返回空列表，被显式置 {@code null} 时可能返回 {@code null}
     */
    public List<String> getGatewayIgnoreUrl() {
        return properties.getGatewayIgnoreUrl();
    }

    /**
     * 设置网关层忽略路径。
     *
     * <p>由网关或平台统一下发的放行清单，本服务直接信任并跳过校验，典型如网关自身回调、跨服务内部调用入口。
     * 传入 {@code null} 会清空原配置。</p>
     *
     * @param gatewayIgnoreUrl 网关级忽略路径列表，允许为 {@code null}
     */
    public void setGatewayIgnoreUrl(List<String> gatewayIgnoreUrl) {
        properties.setGatewayIgnoreUrl(gatewayIgnoreUrl);
    }

    /**
     * 获取业务自定义放行路径列表。
     *
     * @return 业务自定义忽略路径；未配置时返回空列表，被显式置 {@code null} 时可能返回 {@code null}
     */
    public List<String> getCustomIgnoreUrl() {
        return properties.getCustomIgnoreUrl();
    }

    /**
     * 设置业务自定义放行路径。
     *
     * <p>供运维或租户在运行时动态追加的忽略清单（如第三方回调、消息通知接收端点），优先级叠加在通用/网关清单之上。
     * 传入 {@code null} 会清空原配置。</p>
     *
     * @param customIgnoreUrl 业务自定义忽略路径列表，允许为 {@code null}
     */
    public void setCustomIgnoreUrl(List<String> customIgnoreUrl) {
        properties.setCustomIgnoreUrl(customIgnoreUrl);
    }

    /**
     * 获取是否开启细粒度权限校验的总开关。
     *
     * @return 是否校验权限；默认 {@code true}，被显式置 {@code null} 时可能返回 {@code null}
     */
    public Boolean getVerifyPermission() {
        return properties.getVerifyPermission();
    }

    /**
     * 设置是否开启细粒度权限校验的总开关。
     *
     * <p>关闭后 WebAuthFilter 仅校验 Token 合法性、不再执行菜单/接口权限判定，适用于纯身份认证场景或不依赖 RBAC 的内部服务。
     * 该值为 {@code null} 时由 {@link AuthFilterProperties} 的默认值决定行为。</p>
     *
     * @param verifyPermission 是否校验权限，允许为 {@code null}
     */
    public void setVerifyPermission(Boolean verifyPermission) {
        properties.setVerifyPermission(verifyPermission);
    }

    /**
     * 获取仅校验 Token、跳过权限判定的路径列表。
     *
     * @return 仅校验 Token 的路径；未配置时返回空列表，被显式置 {@code null} 时可能返回 {@code null}
     */
    public List<String> getOnlyVerifyToken() {
        return properties.getOnlyVerifyToken();
    }

    /**
     * 设置仅校验 Token 的路径列表。
     *
     * <p>命中该清单的请求完成身份解析后直接放行、跳过菜单/接口权限判定，用于已登录但无需 RBAC 校验的轻量接口。
     * 传入 {@code null} 会清空原配置。</p>
     *
     * @param onlyVerifyToken 仅校验 Token 的路径列表，允许为 {@code null}
     */
    public void setOnlyVerifyToken(List<String> onlyVerifyToken) {
        properties.setOnlyVerifyToken(onlyVerifyToken);
    }

    /**
     * 获取所有忽略的 URL 集合（去重）
     * @return 所有忽略的 URL
     */
    public Set<String> getAllIgnoreUrls() {
        Set<String> allUrls = new HashSet<>();
        allUrls.addAll(FilterIgnoreConstant.getCommonIgnoreUrls());
        allUrls.addAll(properties.getCommonIgnoreUrl());
        allUrls.addAll(properties.getGatewayIgnoreUrl());
        allUrls.addAll(properties.getCustomIgnoreUrl());
        return allUrls;
    }

    /**
     * 检查指定 URL 是否应该被忽略
     * @param url 待检查的 URL
     * @return 如果应该忽略返回 true，否则返回 false
     */
    public boolean shouldIgnoreUrl(String url) {
        return UrlPathUtils.isIgnoreUrl(FilterIgnoreConstant.getCommonIgnoreUrls(), url) ||
                UrlPathUtils.isIgnoreUrl(properties.getCommonIgnoreUrl(), url) ||
                UrlPathUtils.isIgnoreUrl(properties.getGatewayIgnoreUrl(), url) ||
                UrlPathUtils.isIgnoreUrl(properties.getCustomIgnoreUrl(), url);
    }
}