package com.njydsz.common.base.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * 文档安全自动配置类
 *
 * <p>为 API 文档（Swagger/Knife4j）提供生产环境访问控制：
 *
 * <ul>
 *   <li>生产环境默认关闭文档访问（{@code ydsz.doc.production-enabled=false}）
 *   <li>开启时自动启用 Basic 认证保护（可通过 {@code ydsz.doc.basic-auth.enabled=false} 关闭）
 *   <li>用户名/密码可通过 {@code ydsz.doc.basic-auth.username} 和 {@code ydsz.doc.basic-auth.password} 配置
 * </ul>
 *
 * <p><b>配置示例：</b>
 *
 * <pre>
 * # 生产环境开启文档访问
 * ydsz.doc.production-enabled: true
 * # Basic 认证配置
 * ydsz.doc.basic-auth.enabled: true
 * ydsz.doc.basic-auth.username: admin
 * ydsz.doc.basic-auth.password: your-secure-password
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@AutoConfiguration
@Slf4j
@EnableConfigurationProperties(DocProperties.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(
    prefix = "ydsz.doc",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = false)
public class DocSecurityConfiguration {

  /** HTTP Basic 认证头前缀 */
  private static final String BASIC_PREFIX = "Basic ";

  /** 文档模块配置属性，由 Spring 注入 */
  private final DocProperties docProperties;

  /** Spring 环境对象，用于获取激活的 Profile */
  private final Environment environment;

  /**
   * 构造方法
   *
   * <p>在构造阶段即对生产环境安全配置进行校验与告警，避免运行期才暴露安全风险。
   *
   * @param docProperties 文档模块配置属性
   * @param environment Spring 环境对象
   */
  public DocSecurityConfiguration(DocProperties docProperties, Environment environment) {
    this.docProperties = docProperties;
    this.environment = environment;
    // 生产环境安全警告：文档功能启用时应确保有安全保护
    checkProductionSecurity(docProperties);
  }

  /**
   * 检查生产环境文档安全配置
   *
   * <p>通过 Spring {@link Environment} 获取当前激活的 Profile， 若包含 {@code prod} 或 {@code production}，
   * 会根据当前配置组合输出不同级别的安全告警日志。
   *
   * @param props 文档配置属性
   */
  private void checkProductionSecurity(DocProperties props) {
    boolean isProduction = false;
    for (String profile : environment.getActiveProfiles()) {
      if (profile.contains("prod") || profile.contains("production")) {
        isProduction = true;
        break;
      }
    }
    if (isProduction) {
      if (!props.isProductionEnabled()) {
        log.warn(
            "【文档安全】生产环境检测到 ydsz.doc.enabled=true 但 production-enabled=false，"
                + "文档功能已启用但生产环境访问控制未开启，请确认是否符合安全要求");
      } else if (!props.getBasicAuth().isEnabled()) {
        log.warn(
            "【文档安全】生产环境文档访问控制已开启，但 Basic 认证已关闭，" + "存在安全风险！建议设置 ydsz.doc.basic-auth.enabled=true");
      } else {
        log.warn("【文档安全】生产环境文档功能已启用，请确保仅在必要时开启并配置强密码保护");
      }
    }
  }

  /**
   * 注册文档 Basic 认证过滤器
   *
   * <p>拦截所有文档相关路径，验证 Basic 认证凭证。 URL 拦截路径从 {@link DocProperties} 动态构建，跟随配置变化。
   *
   * @return FilterRegistrationBean 实例
   */
  @Bean
  @ConditionalOnProperty(prefix = "ydsz.doc", name = "production-enabled", havingValue = "true")
  public FilterRegistrationBean<Filter> docBasicAuthFilter() {
    DocProperties.BasicAuth basicAuth = docProperties.getBasicAuth();

    log.info("========================================");
    log.info("文档安全访问控制已启用");
    log.info("  - 生产环境: 已开启");
    if (basicAuth.isEnabled()) {
      log.info("  - Basic 认证: 已启用 [user: {}]", basicAuth.getUsername());
    } else {
      log.warn("  - Basic 认证: 已关闭（生产环境不推荐）");
    }
    log.info("========================================");

    FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
    registration.setFilter(new DocBasicAuthFilter(basicAuth));
    registration.addUrlPatterns(buildDocUrlPatterns().toArray(new String[0]));
    registration.setName("docBasicAuthFilter");
    registration.setOrder(1);
    return registration;
  }

  /**
   * 从 DocProperties 动态构建文档 URL 拦截路径
   *
   * <p>包含以下路径：
   *
   * <ul>
   *   <li>Knife4j 文档入口（{@code ydsz.doc.knife4j-path}）
   *   <li>OpenAPI 文档 JSON（{@code ydsz.doc.api-docs-path} 及其子路径）
   *   <li>Swagger UI 静态资源
   *   <li>WebJars 静态资源
   * </ul>
   *
   * @return URL 拦截路径列表
   */
  private List<String> buildDocUrlPatterns() {
    List<String> patterns = new ArrayList<>(16);
    // Knife4j 入口
    patterns.add(docProperties.getKnife4jPath());
    // OpenAPI JSON 入口及子路径
    String apiDocsPath = docProperties.getApiDocsPath();
    patterns.add(apiDocsPath);
    patterns.add(apiDocsPath + "/*");
    // Swagger UI（兼容 springdoc）
    patterns.add("/swagger-ui/*");
    patterns.add("/swagger-ui.html");
    // WebJars 静态资源
    patterns.add("/webjars/*");
    return patterns;
  }

  /**
   * 文档 Basic 认证过滤器实现
   *
   * <p>基于 Servlet {@link Filter} 实现，校验请求头中的 {@code Authorization} 字段是否与配置的账号密码匹配。比对采用 {@link
   * MessageDigest#isEqual(byte[], byte[])} 恒定时间比较， 避免时序攻击泄露前缀信息。
   *
   * <p><b>线程安全性：</b>无状态，线程安全。
   *
   * @author ydsz-team
   * @since 26.09.01
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
      this.expectedCredentials =
          Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
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

      httpResponse.setHeader("WWW-Authenticate", "Basic realm=\"YDSZ API Docs\"");
      httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
    }
  }
}
