package com.njydsz.pmis.common.auth.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * 认证过滤器配置属性
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
@Data
@ConfigurationProperties(prefix = "ydsz.auth.filter")
public class AuthFilterProperties {

    private List<String> commonIgnoreUrl = new ArrayList<>();

    private List<String> gatewayIgnoreUrl = new ArrayList<>();

    private List<String> customIgnoreUrl = new ArrayList<>();

    private Boolean verifyPermission = true;

    private List<String> onlyVerifyToken = new ArrayList<>();
}