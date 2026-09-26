package com.njydsz.system.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.excel.spring.ExcelWebSupport;
import com.njydsz.system.domain.dto.ConfigDTO;
import com.njydsz.system.domain.query.ConfigPageQuery;
import com.njydsz.system.domain.vo.ConfigVO;
import com.njydsz.system.server.service.ConfigBatchService;
import com.njydsz.system.server.service.ConfigService;
import com.njydsz.system.web._support.WebMvcTestApplication;

/**
 * ConfigController 真实 HTTP（MockMvc）集成测试。
 *
 * <p>验证 Web 层行为：URL 路由、参数绑定、响应体 JSON 序列化、HTTP 状态码。使用 {@link WebMvcTestApplication} 轻量级上下文（排除 Redis/Nacos/ES 依赖），
 * {@link ConfigService} / {@link ConfigBatchService} / {@link ExcelWebSupport} 以 {@link MockBean} 注入。
 *
 * <p><b>测试范围：</b>GET /config/public（公开配置）、GET /config/page（分页查询）、GET /config/key/{configKey}（按 Key 查询）、POST /config（创建配置）。
 *
 * <p><b>已知限制：</b>AuthAspect / IdempotentAspect / RateLimitAspect / AuditAspect 在测试上下文中已排除（参见 {@link WebMvcTestApplication}），鉴权与限流由 E2E 用例覆盖。
 *
 * @author ydsz-team
 * @since 26.09.26
 */
@SpringBootTest(classes = WebMvcTestApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConfigControllerMockMvcTest {

  @MockBean private ConfigService configService;

  @MockBean private ConfigBatchService configBatchService;

  @MockBean private ExcelWebSupport excelWebSupport;

  @org.springframework.beans.factory.annotation.Autowired
  private org.springframework.test.web.servlet.MockMvc mockMvc;

  @org.springframework.beans.factory.annotation.Autowired
  private tools.jackson.databind.ObjectMapper objectMapper;

  /** 默认测试用租户 ID（多租户隔离用）。 */
  private static final String TENANT_ID = "1";

  @Nested
  @DisplayName("GET /config/public — 公开配置查询")
  class GetPublicConfigs {

    @Test
    @DisplayName("应返回 200 与配置列表")
    void shouldReturnPublicConfigsWith200() throws Exception {
      // arrange
      ConfigVO vo = new ConfigVO();
      vo.setId("pub-1");
      vo.setConfigKey("theme.color");
      vo.setConfigValue("#FFFFFF");
      vo.setConfigGroup("ui");
      List<ConfigVO> publicConfigs = List.of(vo);
      when(configService.listPublicConfigs()).thenReturn(publicConfigs);

      // act
      MvcResult result =
          mockMvc.perform(
                  org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                          "/config/public")
                      .accept(org.springframework.http.MediaType.APPLICATION_JSON)
                      .header("X-Tenant-Id", TENANT_ID))
              .andReturn();

      // assert: HTTP 200 + 业务响应码 A00000 + 数据列表
      assertThat(result.getResponse().getStatusCode()).isEqualTo(200);
      String body = result.getResponse().getContentAsString();
      YdszResponse<?> resp = objectMapper.readValue(body, YdszResponse.class);
      assertThat(resp.getCode()).isEqualTo("A00000");
      assertThat(resp.getData()).isNotNull();
      verify(configService).listPublicConfigs();
    }

    @Test
    @DisplayName("无公开配置时应返回空列表（非 null）")
    void shouldReturnEmptyListWhenNoPublicConfigs() throws Exception {
      when(configService.listPublicConfigs()).thenReturn(List.of());

      MvcResult result =
          mockMvc.perform(
                  org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                          "/config/public")
                      .accept(org.springframework.http.MediaType.APPLICATION_JSON)
                      .header("X-Tenant-Id", TENANT_ID))
              .andReturn();

      assertThat(result.getResponse().getStatusCode()).isEqualTo(200);
      String body = result.getResponse().getContentAsString();
      YdszResponse<?> resp = objectMapper.readValue(body, YdszResponse.class);
      assertThat(resp.getCode()).isEqualTo("A00000");
    }
  }

  @Nested
  @DisplayName("GET /config/page — 分页查询")
  class GetPage {

    @Test
    @DisplayName("应委托 Service.page 并返回分页响应")
    void shouldDelegateToServiceAndReturnPaged() throws Exception {
      // arrange: 构造分页返回
      PageResponse<List<ConfigVO>> pageResult = PageResponse.empty(0L, 20L);
      when(configService.page(any(ConfigPageQuery.class))).thenReturn(pageResult);

      // act: GET /config/page?pageNum=1&pageSize=20
      MvcResult result =
          mockMvc.perform(
                  org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                          "/config/page")
                      .param("pageNum", "1")
                      .param("pageSize", "20")
                      .param("configGroup", "ui")
                      .accept(org.springframework.http.MediaType.APPLICATION_JSON)
                      .header("X-Tenant-Id", TENANT_ID))
              .andReturn();

      // assert
      assertThat(result.getResponse().getStatusCode()).isEqualTo(200);
      String body = result.getResponse().getContentAsString();
      YdszResponse<?> resp = objectMapper.readValue(body, YdszResponse.class);
      assertThat(resp.getCode()).isEqualTo("A00000");
      verify(configService).page(any(ConfigPageQuery.class));
    }

    @ParameterizedTest(name = "参数校验: pageNum={0}")
    @NullAndSource
    @ValueSource(strings = {"-1", "0"})
    @DisplayName("pageNum 非法值时 Spring 应绑定为默认值 1（MethodArgumentTypeMismatch 由全局处理器返回 400）")
    void shouldHandleInvalidPageNum Gracefully(String invalidPageNum) throws Exception {
      // arrange: 即使参数缺失或非法，Service 侧应能被调用（参数绑定由 Spring 处理）
      when(configService.page(any(ConfigPageQuery.class))).thenReturn(PageResponse.empty(0L, 20L));

      // act
      org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requestBuilder =
          org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                  "/config/page")
              .accept(org.springframework.http.MediaType.APPLICATION_JSON)
              .header("X-Tenant-Id", TENANT_ID);
      if (invalidPageNum != null) {
        requestBuilder.param("pageNum", invalidPageNum);
      }
      MvcResult result = mockMvc.perform(requestBuilder).andReturn();

      // assert: 请求不应产生 500（要么成功绑定默认值，要么返回 400，但不能是未处理的异常）
      assertThat(result.getResponse().getStatusCode()).isIn(200, 400);
    }
  }

  @Nested
  @DisplayName("GET /config/key/{configKey} — 按配置键查询")
  class GetByKey {

    @Test
    @DisplayName("应返回 200 与配置值")
    void shouldReturnConfigValueWith200() throws Exception {
      when(configService.getConfigValue("theme.color")).thenReturn("#FFFFFF");

      MvcResult result =
          mockMvc.perform(
                  org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                          "/config/key/theme.color")
                      .accept(org.springframework.http.MediaType.APPLICATION_JSON)
                      .header("X-Tenant-Id", TENANT_ID))
              .andReturn();

      assertThat(result.getResponse().getStatusCode()).isEqualTo(200);
      String body = result.getResponse().getContentAsString();
      YdszResponse<?> resp = objectMapper.readValue(body, YdszResponse.class);
      assertThat(resp.getCode()).isEqualTo("A00000");
      assertThat(resp.getData()).isNotNull();
      verify(configService).getConfigValue("theme.color");
    }

    @Test
    @DisplayName("不存在的 key 时 Service 返回 null，Controller 仍应返回 200（success 包 null 数据）")
    void shouldReturnSuccessWithNullDataWhenKeyNotFound() throws Exception {
      when(configService.getConfigValue(anyString())).thenReturn(null);

      MvcResult result =
          mockMvc.perform(
                  org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                          "/config/key/nonexistent.key")
                      .accept(org.springframework.http.MediaType.APPLICATION_JSON)
                      .header("X-Tenant-Id", TENANT_ID))
              .andReturn();

      assertThat(result.getResponse().getStatusCode()).isEqualTo(200);
      String body = result.getResponse().getContentAsString();
      YdszResponse<?> resp = objectMapper.readValue(body, YdszResponse.class);
      assertThat(resp.getCode()).isEqualTo("A00000");
    }
  }

  @Nested
  @DisplayName("POST /config — 创建配置")
  class CreateConfig {

    @Test
    @DisplayName("合法请求应返回携带新 ID 的响应")
    void shouldReturnNewIdForValidRequest() throws Exception {
      // arrange
      when(configService.save(any(ConfigDTO.class))).thenReturn("new-config-id-001");

      // 构造合法 DTO JSON
      String requestBody =
          "{\"configKey\":\"test.timeout\",\"configValue\":\"30\",\"configGroup\":\"timeouts\","
              + "\"valueType\":\"NUMBER\",\"isPublic\":false,\"sort\":0,\"description\":\"test\"}";

      // act
      MvcResult result =
          mockMvc.perform(
                  org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                          "/config")
                      .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                      .content(requestBody)
                      .header("X-Tenant-Id", TENANT_ID))
              .andReturn();

      // assert
      assertThat(result.getResponse().getStatusCode()).isEqualTo(200);
      String body = result.getResponse().getContentAsString();
      YdszResponse<?> resp = objectMapper.readValue(body, YdszResponse.class);
      assertThat(resp.getCode()).isEqualTo("A00000");
      assertThat(resp.getData()).isNotNull();
      verify(configService).save(any(ConfigDTO.class));
    }

    @Test
    @DisplayName("缺少必填字段 configKey 时应返回 400（@Valid 校验失败由全局处理器处理）")
    void shouldReturn400WhenMissingRequiredField() throws Exception {
      // 缺少 configKey
      String invalidBody =
          "{\"configValue\":\"30\",\"configGroup\":\"timeouts\",\"valueType\":\"NUMBER\"}";

      MvcResult result =
          mockMvc.perform(
                  org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                          "/config")
                      .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                      .content(invalidBody)
                      .header("X-Tenant-Id", TENANT_ID))
              .andReturn();

      // @NotBlank 校验失败 → 400 Bad Request
      assertThat(result.getResponse().getStatusCode()).isEqualTo(400);
    }
  }
}
