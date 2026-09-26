package com.njydsz.system.server._support;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.njydsz.common.core.response.YdszResponse;

import tools.jackson.databind.ObjectMapper;

/**
 * Controller 测试基类（共享 MockMvc 配置与常用断言辅助）。
 *
 * <p><b>Spring Boot 4.x 适配：</b>使用 {@code spring-boot-starter-webmvc-test} 引入 {@link AutoConfigureMockMvc}，
 * 包路径为 {@code org.springframework.boot.webmvc.test.autoconfigure}（Spring Boot 4.0 将 autoconfigure 拆为 47 个子模块）。
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
   * @param result MockMvc 执行结果
   * @param expectedCode 期望的响应码
   * @throws Exception JSON 解析失败时抛出
   */
  protected void expectCode(MvcResult result, String expectedCode) throws Exception {
    String body = result.getResponse().getContentAsString();
    YdszResponse<?> resp = objectMapper.readValue(body, YdszResponse.class);
    assertThat(resp.getCode()).isEqualTo(expectedCode);
  }

  /**
   * 构建携带 X-Tenant-Id Header 的 GET 请求（JSON accept）。
   *
   * @param url 请求 URL
   * @return MockMvc GET 请求构建器
   */
  protected MockHttpServletRequestBuilder getJson(String url) {
    return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url)
        .accept(MediaType.APPLICATION_JSON)
        .header("X-Tenant-Id", DEFAULT_TEST_TENANT);
  }

  /**
   * 构建 POST 请求（JSON content-type + body + 鉴权 Header）。
   *
   * @param url 请求 URL
   * @param body 请求体对象
   * @return MockMvc POST 请求构建器
   * @throws Exception JSON 序列化失败时抛出
   */
  protected MockHttpServletRequestBuilder postJson(String url, Object body) throws Exception {
    return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(body))
        .header("X-Tenant-Id", DEFAULT_TEST_TENANT);
  }

  /**
   * 构建 PUT 请求（JSON content-type + body + 鉴权 Header）。
   *
   * @param url 请求 URL
   * @param body 请求体对象
   * @return MockMvc PUT 请求构建器
   * @throws Exception JSON 序列化失败时抛出
   */
  protected MockHttpServletRequestBuilder putJson(String url, Object body) throws Exception {
    return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(url)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(body))
        .header("X-Tenant-Id", DEFAULT_TEST_TENANT);
  }

  /**
   * 构建 DELETE 请求（JSON accept + 鉴权 Header）。
   *
   * @param url 请求 URL
   * @return MockMvc DELETE 请求构建器
   */
  protected MockHttpServletRequestBuilder deleteJson(String url) {
    return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(url)
        .accept(MediaType.APPLICATION_JSON)
        .header("X-Tenant-Id", DEFAULT_TEST_TENANT);
  }
}
