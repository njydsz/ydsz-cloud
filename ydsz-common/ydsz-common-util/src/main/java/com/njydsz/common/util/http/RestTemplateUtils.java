package com.njydsz.common.util.http;

import java.time.Duration;
import java.util.Arrays;

import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate 统一构建工厂。
 *
 * <p>平台内所有需要手工构建 {@link RestTemplate} 的场景（第三方通道对接、服务节点调用等）
 * 统一通过本工厂创建，保证超时配置、拦截器（SSRF 防护、trace 透传）挂载方式一致。
 * 禁止业务代码直接 {@code new RestTemplate(...)} 绕过容器与统一工厂。</p>
 *
 * <p><b>使用示例：</b></p>
 *
 * <pre>{@code
 * // 毫秒超时 + SSRF 拦截器
 * RestTemplate restTemplate = RestTemplateUtils.create(
 *     connectTimeoutMs, readTimeoutMs, new SsrfHttpRequestInterceptor());
 * }</pre>
 *
 * <p>本类无状态，线程安全。</p>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class RestTemplateUtils {

  private RestTemplateUtils() {
    throw new UnsupportedOperationException("RestTemplateUtils is a utility class");
  }

  /**
   * 创建带超时配置的 RestTemplate（毫秒精度）。
   *
   * @param connectTimeoutMs 连接超时（毫秒），必须为正数
   * @param readTimeoutMs 读取超时（毫秒），必须为正数
   * @param interceptors 可选拦截器（如 SSRF 防护、trace 透传），按传入顺序挂载
   * @return 已配置超时与拦截器的 RestTemplate
   */
  public static RestTemplate create(
      int connectTimeoutMs, int readTimeoutMs, ClientHttpRequestInterceptor... interceptors) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(connectTimeoutMs);
    factory.setReadTimeout(readTimeoutMs);
    return assemble(factory, interceptors);
  }

  /**
   * 创建带超时配置的 RestTemplate（Duration 精度）。
   *
   * @param connectTimeout 连接超时，必须为正数
   * @param readTimeout 读取超时，必须为正数
   * @param interceptors 可选拦截器（如 SSRF 防护、trace 透传），按传入顺序挂载
   * @return 已配置超时与拦截器的 RestTemplate
   */
  public static RestTemplate create(
      Duration connectTimeout, Duration readTimeout, ClientHttpRequestInterceptor... interceptors) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(connectTimeout);
    factory.setReadTimeout(readTimeout);
    return assemble(factory, interceptors);
  }

  /**
   * 挂载拦截器并组装 RestTemplate。
   *
   * @param factory 请求工厂
   * @param interceptors 拦截器（可为空数组）
   * @return 组装完成的 RestTemplate
   */
  private static RestTemplate assemble(
      SimpleClientHttpRequestFactory factory, ClientHttpRequestInterceptor... interceptors) {
    RestTemplate restTemplate = new RestTemplate(factory);
    if (interceptors != null && interceptors.length > 0) {
      restTemplate.getInterceptors().addAll(Arrays.asList(interceptors));
    }
    return restTemplate;
  }
}
