package com.njydsz.common.auth.config;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.njydsz.common.core.constant.FilterIgnoreConstant;
import com.njydsz.common.util.url.UrlPathUtils;

/**
 * 认证过滤器配置类
 * 用于配置不同层级的 URL 忽略规则
 *
 * @since 1.0.0
 * 
 */
@AutoConfiguration
@EnableConfigurationProperties(AuthFilterProperties.class)
public class AuthFilterConfiguration {

    private final AuthFilterProperties properties;

    public AuthFilterConfiguration(AuthFilterProperties properties) {
        this.properties = properties;
    }

    public List<String> getCommonIgnoreUrl() {
        return properties.getCommonIgnoreUrl();
    }

    public void setCommonIgnoreUrl(List<String> commonIgnoreUrl) {
        properties.setCommonIgnoreUrl(commonIgnoreUrl);
    }

    public List<String> getGatewayIgnoreUrl() {
        return properties.getGatewayIgnoreUrl();
    }

    public void setGatewayIgnoreUrl(List<String> gatewayIgnoreUrl) {
        properties.setGatewayIgnoreUrl(gatewayIgnoreUrl);
    }

    public List<String> getCustomIgnoreUrl() {
        return properties.getCustomIgnoreUrl();
    }

    public void setCustomIgnoreUrl(List<String> customIgnoreUrl) {
        properties.setCustomIgnoreUrl(customIgnoreUrl);
    }

    public Boolean getVerifyPermission() {
        return properties.getVerifyPermission();
    }

    public void setVerifyPermission(Boolean verifyPermission) {
        properties.setVerifyPermission(verifyPermission);
    }

    public List<String> getOnlyVerifyToken() {
        return properties.getOnlyVerifyToken();
    }

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