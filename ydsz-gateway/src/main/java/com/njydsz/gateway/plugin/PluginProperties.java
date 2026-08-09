package com.njydsz.gateway.plugin;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

import lombok.Data;

/**
 * P2-1: 插件系统配置属性
 *
 * @since 1.0.0 (P2-1)
 * @author ydsz-team
 */
@Data
@RefreshScope
@ConfigurationProperties(prefix = "ydsz.gateway.plugin")
public class PluginProperties {

    /** 是否启用插件系统 */
    private boolean enabled = false;

    /** 插件目录路径（监控热加载） */
    private String watchPath = "./plugins";

    /** 目录扫描间隔（毫秒） */
    private long reloadInterval = 5000;
}
