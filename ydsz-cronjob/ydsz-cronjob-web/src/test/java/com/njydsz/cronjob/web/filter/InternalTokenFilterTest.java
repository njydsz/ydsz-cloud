package com.njydsz.cronjob.web.filter;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.njydsz.cronjob.domain.constants.CronjobConstants;
import com.njydsz.cronjob.server.config.CronjobProperties;
import com.njydsz.cronjob.server.config.RemoteConfig;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link InternalTokenFilter} 单元测试。
 *
 * <p>覆盖路径匹配与令牌校验两条主链路，重点守护 P0-1：
 * 过滤器必须真正拦截 {@code /cronjob/internal/**}（历史上因常量含 {@code /api} 而恒不命中）。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
class InternalTokenFilterTest {

  private static final String VALID_TOKEN = "test-internal-token";

  private CronjobProperties properties;
  private InternalTokenFilter filter;

  @BeforeEach
  void setUp() {
    properties = new CronjobProperties();
    RemoteConfig remote = new RemoteConfig();
    remote.setAccessToken(VALID_TOKEN);
    remote.setAllowEmptyToken(false);
    properties.setRemote(remote);
    filter = new InternalTokenFilter(properties);
  }

  @Test
  @DisplayName("内部派发路径必须被过滤器拦截（shouldNotFilter=false）")
  void shouldFilterInternalDispatchPaths() throws ServletException, IOException {
    assertThat(filter.shouldNotFilter(request(CronjobConstants.INTERNAL_EXECUTE_PATH))).isFalse();
    assertThat(filter.shouldNotFilter(request(CronjobConstants.INTERNAL_SUB_TASK_PATH))).isFalse();
    assertThat(filter.shouldNotFilter(request(CronjobConstants.INTERNAL_BATCH_PATH))).isFalse();
  }

  @Test
  @DisplayName("非内部路径必须放行（shouldNotFilter=true）")
  void shouldNotFilterExternalPaths() {
    assertThat(filter.shouldNotFilter(request("/cronjob/jobs"))).isTrue();
    assertThat(filter.shouldNotFilter(request("/cronjob/internalx/execute"))).isTrue();
    assertThat(filter.shouldNotFilter(request("/actuator/health"))).isTrue();
  }

  @Test
  @DisplayName("携带正确令牌放行")
  void doFilterWithValidToken() throws ServletException, IOException {
    MockHttpServletRequest request = request(CronjobConstants.INTERNAL_EXECUTE_PATH);
    request.addHeader(CronjobConstants.INTERNAL_TOKEN_HEADER, VALID_TOKEN);
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  @DisplayName("缺少令牌返回 401")
  void doFilterWithoutToken() throws ServletException, IOException {
    MockHttpServletRequest request = request(CronjobConstants.INTERNAL_EXECUTE_PATH);
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(response.getStatus()).isEqualTo(401);
  }

  @Test
  @DisplayName("令牌不匹配返回 401")
  void doFilterWithWrongToken() throws ServletException, IOException {
    MockHttpServletRequest request = request(CronjobConstants.INTERNAL_EXECUTE_PATH);
    request.addHeader(CronjobConstants.INTERNAL_TOKEN_HEADER, "wrong-token");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(response.getStatus()).isEqualTo(401);
  }

  @Test
  @DisplayName("未配置令牌时 fail-closed 返回 401")
  void doFilterFailClosedWhenTokenBlank() throws ServletException, IOException {
    RemoteConfig remote = new RemoteConfig();
    remote.setAccessToken("");
    remote.setAllowEmptyToken(false);
    properties.setRemote(remote);

    MockHttpServletRequest request = request(CronjobConstants.INTERNAL_EXECUTE_PATH);
    request.addHeader(CronjobConstants.INTERNAL_TOKEN_HEADER, VALID_TOKEN);
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(response.getStatus()).isEqualTo(401);
  }

  /**
   * 构造内部派发请求。
   *
   * @param uri 请求路径
   * @return Mock 请求
   */
  private MockHttpServletRequest request(String uri) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
    request.setRequestURI(uri);
    return request;
  }
}
