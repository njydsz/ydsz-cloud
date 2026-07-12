package com.njydsz.pmis.common.doc.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

/**
 * 文档安全自动配置类
 *
 * <p>为 API 文档（Swagger/Knife4j）提供生产环境访问控制：
 * <ul>
 *   <li>生产环境默认关闭文档访问（{@code remi.doc.production-enabled=false}）</li>
 *   <li>开启时自动启用 Basic 认证保护（可通过 {@code remi.doc.basic-auth.enabled=false} 关闭）</li>
 *   <li>用户名/密码可通过 {@code remi.doc.basic-auth.username} 和 {@code remi.doc.basic-auth.password} 配置</li>
 * </ul>
 *
 * <p><b>配置示例：</b>
 * <pre>
 * # 生产环境开启文档访问
 * remi.doc.production-enabled: true
 * # Basic 认证配置
 * remi.doc.basic-auth.enabled: true
 * remi.doc.basic-auth.username: admin
 * remi.doc.basic-auth.password: your-secure-password
 * </pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(DocProperties.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "remi.doc", name = "enabled", havingValue = "true", matchIfMissing = false)
public class DocSecurityConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(DocSecurityConfiguration.class);

    /** HTTP Basic 认证头前缀 */
    private static final String BASIC_PREFIX = "Basic ";

    /** 文档模块配置属性，由 Spring 注入 */
    private final DocProperties docProperties;

    /**
     * 构造方法
     *
     * <p>在构造阶段即对生产环境安全配置进行校验与告警，避免运行期才暴露安全风险。
     *
     * @param docProperties 文档模块配置属性
     */
    public DocSecurityConfiguration(DocProperties docProperties) {
        this.docProperties = docProperties;
        // 生产环境安全警告：文档功能启用时应确保有安全保护
        checkProductionSecurity(docProperties);
    }

    /**
     * 检查生产环境文档安全配置
     *
     * <p>若检测到当前激活的 Profile 包含 {@code prod} 或 {@code production}，
     * 会根据当前配置组合输出不同级别的安全告警日志。
     *
     * @param props 文档配置属性
     */
    private void checkProductionSecurity(DocProperties props) {
        String activeProfile = System.getProperty("spring.profiles.active",
                System.getenv("SPRING_PROFILES_ACTIVE"));
        if (activeProfile != null && (activeProfile.contains("prod") || activeProfile.contains("production"))) {
            if (!props.isProductionEnabled()) {
                logger.warn("【文档安全】生产环境检测到 remi.doc.enabled=true 但 production-enabled=false，"
                        + "文档功能已启用但生产环境访问控制未开启，请确认是否符合安全要求");
            } else if (!props.getBasicAuth().isEnabled()) {
                logger.warn("【文档安全】生产环境文档访问控制已开启，但 Basic 认证已关闭，"
                        + "存在安全风险！建议设置 remi.doc.basic-auth.enabled=true");
            } else {
                logger.warn("【文档安全】生产环境文档功能已启用，请确保仅在必要时开启并配置强密码保护");
            }
        }
    }

    /**
     * 注册文档 Basic 认证过滤器
     *
     * <p>拦截所有文档相关路径，验证 Basic 认证凭证。
     *
     * @return FilterRegistrationBean 实例
     */
    @Bean
    @ConditionalOnProperty(prefix = "remi.doc", name = "production-enabled", havingValue = "true")
    public FilterRegistrationBean<Filter> docBasicAuthFilter() {
        DocProperties.BasicAuth basicAuth = docProperties.getBasicAuth();

        logger.info("========================================");
        logger.info("文档安全访问控制已启用");
        logger.info("  - 生产环境: 已开启");
        if (basicAuth.isEnabled()) {
            logger.info("  - Basic 认证: 已启用 [user: {}]", basicAuth.getUsername());
        } else {
            logger.warn("  - Basic 认证: 已关闭（生产环境不推荐）");
        }
        logger.info("========================================");

        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new DocBasicAuthFilter(basicAuth));
        registration.addUrlPatterns(
                "/doc.html",
                "/swagger-ui/*",
                "/v3/api-docs/*",
                "/v2/api-docs/*",
                "/webjars/*"
        );
        registration.setName("docBasicAuthFilter");
        registration.setOrder(1);
        return registration;
    }

    /**
     * 文档 Basic 认证过滤器实现
     *
     * <p>基于 Servlet {@link Filter} 实现，校验请求头中的 {@code Authorization}
     * 字段是否与配置的账号密码匹配。比对采用
     * {@link MessageDigest#isEqual(byte[], byte[])} 恒定时间比较，
     * 避免时序攻击泄露前缀信息。
     *
     * <p><b>线程安全性：</b>无状态，线程安全。
     *
     * @author Marvin Lee
     * @since 1.0.0
     */
    private static class DocBasicAuthFilter implements Filter {

        /** 预计算的 Base64 编码后的期望凭据 */
        private final String expectedCredentials;

        /**
         * 构造方法
         *
         * @param basicAuth Basic 认证配置
         */
        DocBasicAuthFilter(DocProperties.BasicAuth basicAuth) {
            String credentials = basicAuth.getUsername() + ":" + basicAuth.getPassword();
            this.expectedCredentials = Base64.getEncoder().encodeToString(
                    credentials.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            HttpServletResponse httpResponse = (HttpServletResponse) response;

            String authorization = httpRequest.getHeader("Authorization");

            if (authorization != null && authorization.startsWith(BASIC_PREFIX)) {
                String credentials = authorization.substring(BASIC_PREFIX.length());
                // 使用恒定时间比较防止时序攻击
                byte[] provided = credentials.getBytes(StandardCharsets.UTF_8);
                byte[] expected = expectedCredentials.getBytes(StandardCharsets.UTF_8);
                if (MessageDigest.isEqual(provided, expected)) {
                    chain.doFilter(request, response);
                    return;
                }
            }

            httpResponse.setHeader("WWW-Authenticate", "Basic realm=\"REMI API Docs\"");
            httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
        }
    }
}
