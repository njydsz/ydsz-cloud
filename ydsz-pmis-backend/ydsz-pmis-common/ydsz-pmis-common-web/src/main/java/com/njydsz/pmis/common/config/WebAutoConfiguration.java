package com.njydsz.pmis.common.config;

import com.njydsz.pmis.common.aspect.ApiMetricsAspect;
import com.njydsz.pmis.common.aspect.DataExportAuditAspect;
import com.njydsz.pmis.common.aspect.OperationLogAspect;
import com.njydsz.pmis.common.exception.GlobalExceptionHandler;
import com.njydsz.pmis.common.health.DatabaseHealthIndicator;
import com.njydsz.pmis.common.health.RedisHealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Web 层自动配置
 *
 * <p>聚合 web 模块所有组件，通过 Spring Boot 3 自动装配机制注册。
 * 引入 {@code ydsz-pmis-common-web} 依赖后自动生效。
 *
 * <p>包含：
 * <ul>
 *   <li>{@link ApiVersionConfig} - API 版本路由</li>
 *   <li>{@link I18nConfig} - 国际化消息源</li>
 *   <li>{@link OpenApiConfig} - OpenAPI 3.0 文档</li>
 *   <li>{@link GlobalExceptionHandler} - 全局异常处理</li>
 *   <li>{@link OperationLogAspect} - 操作日志切面</li>
 *   <li>{@link DataExportAuditAspect} - 数据导出审计切面</li>
 *   <li>{@link ApiMetricsAspect} - API 指标监控切面</li>
 *   <li>{@link RedisHealthIndicator} - Redis 健康检查</li>
 *   <li>{@link DatabaseHealthIndicator} - 数据库健康检查</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import({
    ApiVersionConfig.class,
    I18nConfig.class,
    OpenApiConfig.class,
    GlobalExceptionHandler.class,
    OperationLogAspect.class,
    DataExportAuditAspect.class,
    ApiMetricsAspect.class,
    RedisHealthIndicator.class,
    DatabaseHealthIndicator.class
})
public class WebAutoConfiguration {
}
