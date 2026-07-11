package com.njydsz.pmis.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * API 版本化全局配置（P2-1 落地）。
 *
 * <p>为所有 Controller 的 @RequestMapping 路径自动添加版本前缀（默认 {@code /api/v1}），
 * 实现统一的 API 版本管理，便于未来向后兼容升级。
 *
 * <p>设计要点：
 * <ul>
 *   <li>通过 {@link PathMatchConfigurer#addPathPrefix} 全局添加前缀，无需修改每个 Controller</li>
 *   <li>仅对 {@code com.njydsz.pmis} 包下的 Controller 生效，避免影响 Spring Boot 内置端点</li>
 *   <li>Actuator ({@code /actuator/**})、Swagger ({@code /swagger-ui/**}) 等不受影响</li>
 *   <li>可通过配置 {@code pmis.api.version-prefix} 自定义前缀，设为空字符串则禁用</li>
 *   <li>默认启用，可通过 {@code pmis.api.version-enabled=false} 关闭</li>
 * </ul>
 *
 * <p>版本演进策略：
 * <ul>
 *   <li>v1: 当前版本，所有现有 API</li>
 *   <li>v2: 未来不兼容变更时新增，v1 保留一段时间后废弃</li>
 *   <li>通过 {@code @RequestMapping("/v2/xxx")} 可在单个 Controller 上覆盖版本</li>
 * </ul>
 *
 * <p>前端适配：将 axios baseURL 从 {@code /agent} 改为 {@code /api/v1/agent}，
 * 网关路由需相应配置 StripPrefix 或保持透传。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0 (P2-1)
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "pmis.api", name = "version-enabled", havingValue = "true", matchIfMissing = true)
public class ApiVersionConfig implements WebMvcConfigurer {

    /** API 版本前缀（默认 /api/v1） */
    @Value("${pmis.api.version-prefix:/api/v1}")
    private String versionPrefix;

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        if (versionPrefix == null || versionPrefix.isBlank()) {
            return;
        }
        // 仅对 com.njydsz.pmis 包下的 Controller 添加前缀
        configurer.addPathPrefix(versionPrefix,
                c -> c.getPackageName().startsWith("com.njydsz.pmis"));
        log.info("[ApiVersion] 全局 API 版本前缀已启用: {} (仅对 com.njydsz.pmis 包下的 Controller 生效)", versionPrefix);
    }
}
