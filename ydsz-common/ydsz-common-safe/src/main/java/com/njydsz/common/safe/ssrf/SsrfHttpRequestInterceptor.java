package com.njydsz.common.safe.ssrf;

import com.njydsz.common.safe.ssrf.HttpConnectionValidator.SsrfBlockedException;
import java.io.IOException;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * RestTemplate SSRF 防护拦截器。
 *
 * <p>对所有通过 {@link org.springframework.web.client.RestTemplate} 发出的出站请求 执行 SSRF
 * 校验，阻断对内网地址、元数据服务等的访问。
 *
 * <p><b>使用方式：</b>
 *
 * <pre>{@code
 * RestTemplate restTemplate = new RestTemplate();
 * restTemplate.getInterceptors().add(new SsrfHttpRequestInterceptor());
 * }</pre>
 *
 * <p>或通过 Spring 自动配置全局启用：
 *
 * <pre>{@code
 * @Bean
 * public RestTemplateCustomizer ssrfRestTemplateCustomizer() {
 *     return restTemplate -> restTemplate.getInterceptors().add(new SsrfHttpRequestInterceptor());
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.2.0
 * @see HttpConnectionValidator
 */
public class SsrfHttpRequestInterceptor implements ClientHttpRequestInterceptor {

  private final HttpConnectionValidator validator;

  /** 使用默认 SSRF 校验器创建拦截器。 */
  public SsrfHttpRequestInterceptor() {
    this(HttpConnectionValidator.getDefault());
  }

  /**
   * 使用自定义 SSRF 校验器创建拦截器。
   *
   * @param validator 自定义的 SSRF 校验器
   */
  public SsrfHttpRequestInterceptor(HttpConnectionValidator validator) {
    this.validator = validator;
  }

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
    try {
      validator.validate(request.getURI().toString());
    } catch (SsrfBlockedException e) {
      throw new IOException("SSRF blocked: " + e.getMessage(), e);
    }
    return execution.execute(request, body);
  }
}
