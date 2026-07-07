package com.njydsz.pmis.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * IP 白名单安全配置属性（P2-8 安全加固）
 *
 * <p>通过 {@code @RefreshScope} 支持从 Nacos 动态刷新：
 * 当 Nacos 配置变更触发 {@code RefreshEvent} 时，本 Bean 会被重建，
 * 过滤器在下一次请求读取到最新配置，无需重启服务。
 *
 * <p>对应配置项（ydsz-pmis-common.yaml）:
 * <pre>
 * pmis:
 *   security:
 *     ip-whitelist: "192.168.1.0/24,10.0.0.1"
 *     ip-whitelist-enabled: true
 *     ip-whitelist-skip-paths:
 *       - /api/v1/health
 *       - /api/v1/auth/login
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "pmis.security")
public class IpWhitelistProperties {

    /**
     * IP 白名单（逗号分隔，支持 CIDR 与单个 IP）
     *
     * <p>为空表示不启用白名单功能（即使 enabled=true 也放行所有 IP）。
     * 示例: {@code "192.168.1.0/24,10.0.0.1,172.16.0.0/12"}
     */
    private String ipWhitelist = "";

    /**
     * 是否启用 IP 白名单校验
     *
     * <p>即使配置了白名单，也需要此开关为 true 才生效。
     * 默认关闭，避免影响现有环境。
     */
    private boolean ipWhitelistEnabled = false;

    /**
     * 白名单放行的路径前缀（这些路径不校验 IP）
     *
     * <p>用于健康检查、登录等必须公开的端点。
     */
    private List<String> ipWhitelistSkipPaths = new ArrayList<>();
}
