package com.njydsz.system.server._support;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.njydsz.common.core.response.YdszResponse;

import tools.jackson.databind.ObjectMapper;

/**
 * Controller 测试基类（共享 MockMvc 配置与常用断言辅助）。
 *
 * <p>子类继承后获得：
 * <ul>
 *   <li>{@link #mockMvc} — Spring MVC 测试客户端</li>
 *   <li>{@link #objectMapper} — JSON 序列化工具</li>
 *   <li>{@link #expectSuccess(MvcResult)} — 断言响应 code === 'A00000'</li>
 *   <li>{@link #expectCode(MvcResult, String)} — 断言响应 body 中 code 字段为指定值</li>
 *   <li>{@link #getJson(String)} — 构建 GET 请求（JSON accept + 鉴权 Header）</li>
 *   <li>{@link #postJson(String, Object)} — 构建 POST 请求（JSON content-type + body + 鉴权 Header）</li>
 *   <li>{@link #putJson(String, Object)} — 构建 PUT 请求（JSON content-type + body + 鉴权 Header）</li>
 *   <li>{@link #deleteJson(String)} — 构建 DELETE 请求（JSON accept + 鉴权 Header）</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * &#64;SpringBootTest(classes = SystemApplication.class)
 * &#64;AutoConfigureMockMvc
 * class ConfigControllerMockMvcTest extends BaseWebMvcTest {
 *
 *     &#64;MockBean private ConfigService configService;
 *     &#64;MockBean private RbacPermissionEvaluator evaluator;
 *
 *     &#64;Test
 *     void shouldReturnPublicConfigs() throws Exception {
 *         when(configService.listPublicConfigs()).thenReturn(List.of());
 *         MvcResult result = mockMvc.perform(getJson("/config/public")).andReturn();
 *         expectSuccess(result);
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.26
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class BaseWebMvcTest {

  /** 默认测试用租户 ID（多租户隔离用）。 */
  protected static final String DEFAULT_TEST_TENANT = "1";

  @Autowired
  protected MockMvc mockMvc;

  @Autowired
  protected ObjectMapper objectMapper;

  /**
   * 断言响应 body 中 code 字段为 'A00000'（业务成功）。
   *
   * @param result MockMvc 执行结果
   * @throws Exception JSON 解析失败时抛出
   */
  protected void expectSuccess(MvcResult result) throws Exception {
    expectCode(result, "A00000");
  }

  /**
   * 断言响应 body 中 code 字段为指定值。
   *
   * <p>用于验证 AOP 切面（权限不足、限流拒绝、重复提交等）返回特定响应码的场景。
   *
   * @param result MockMvc 执行结果
   * @param expectedCode 期望的响应码（如 "A00000" / "A01052"）
   * @throws Exception JSON 解析失败时抛出
   */
  protected void expectCode(MvcResult result, String expectedCode) throws Exception {
    String body = result.getResponse().getContentAsString();
    YdszResponse<?> resp = objectMapper.readValue(body, YdszResponse.class);
    assertThat(resp.getCode()).isEqualTo(expectedCode);
  }

  /**
   * 构建 GET 请求（携带鉴权 Header + JSON accept）。
   *
   * @param url 请求 URL
   * @return MockMvc GET 请求构建器
   */
  protected MockHttpServletRequestBuilder getJson(String url) {
    return org.springframework.test.web.servlet.request.MockMvcRequestBuilders
        .get(url)
        .accept(MediaType.APPLICATION_JSON)
        .header("X-Tenant-Id", DEFAULT_TEST_TENANT);
  }

  /**
   * 构建 POST 请求（携带鉴权 Header + JSON content-type + body）。
   *
   * @param url 请求 URL
   * @param body 请求体对象（自动序列化为 JSON）
   * @return MockMvc POST 请求构建器
   * @throws Exception JSON 序列化失败时抛出
   */
  protected MockHttpServletRequestBuilder postJson(String url, Object body) throws Exception {
    return org.springframework.test.web.servlet.request.MockMvcRequestBuilders
        .post(url)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(body))
        .header("X-Tenant-Id", DEFAULT_TEST_TENANT);
  }

  /**
   * 构建 PUT 请求（携带鉴权 Header + JSON content-type + body）。
   *
   * @param url 请求 URL
   * @param body 请求体对象（自动序列化为 JSON）
   * @return MockMvc PUT 请求构建器
   * @throws Exception JSON 序列化失败时抛出
   */
  protected MockHttpServletRequestBuilder putJson(String url, Object body) throws Exception {
    return org.springframework.test.web.servlet.request.MockMvcRequestBuilders
        .put(url)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(body))
        .header("X-Tenant-Id", DEFAULT_TEST_TENANT);
  }

  /**
   * 构建 DELETE 请求（携带鉴权 Header + JSON accept）。
   *
   * @param url 请求 URL
   * @return MockMvc DELETE 请求构建器
   */
  protected MockHttpServletRequestBuilder deleteJson(String url) {
    return org.springframework.test.web.servlet.request.MockMvcRequestBuilders
        .delete(url)
        .accept(MediaType.APPLICATION_JSON)
        .header("X-Tenant-Id", DEFAULT_TEST_TENANT);
  }
}
