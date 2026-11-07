package com.njydsz.generator.web.controller;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.exception.code.CoreExceptionCode;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.exception.handler.MvcExceptionHandler;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.generator.entity.GenDatasource;
import com.njydsz.generator.service.DatasourceService;
import com.njydsz.generator.vo.GenDatasourceRespVO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link DatasourceController} Smoke Test。
 *
 * <p>验证数据源管理端点可正确返回 HTTP 200 + YdszResponse 信封结构，
 * 防御接口签名变更未被发现的生产事件。
 *
 * <p><b>补充覆盖（26.11.06）：</b>原文件只覆盖了 5 个端点的 200 路径，未覆盖响应字段、
 * 连接测试的 false 分支、更新端点、参数校验负例与失败路径。本次补齐：
 *
 * <ul>
 *   <li>列表/默认 — 响应 VO 字段透出（id/name/jdbcUrl/dialect/isDefault），且<b>不含 password</b>
 *   <li>test — 连接成功 true / 失败 false 两个分支，失败仍是 HTTP 200 + data=false
 *   <li>create / update — 实体透传、响应 VO 字段；update 的 id 校验负例
 *   <li>delete — 返回 200 且 data 为 null
 *   <li>负例 — @Valid 校验失败 400、Service 抛业务异常错误码透传
 * </ul>
 *
 * @author ydsz-smoke-test
 * @since 26.11.06
 */
class DatasourceControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  private DatasourceService datasourceService;

  private DatasourceController controller;

  @BeforeEach
  void setUp() {
    datasourceService = mock(DatasourceService.class);
    controller = new DatasourceController(datasourceService);
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /generator/datasources")
  class ListDatasources {

    @Test
    @DisplayName("should return 200 when list succeeds")
    void should_return_200_when_list_succeeds() throws Exception {
      GenDatasourceRespVO vo = new GenDatasourceRespVO();
      vo.setId("ds-001");
      vo.setName("测试数据源");
      when(datasourceService.listAllVO()).thenReturn(List.of(vo));

      mockMvc.perform(get("/generator/datasources"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }

    @Test
    @DisplayName("响应 VO 应透出 id/name/jdbcUrl/dialect/isDefault")
    void should_expose_vo_fields() throws Exception {
      GenDatasourceRespVO vo = new GenDatasourceRespVO();
      vo.setId("ds-001");
      vo.setName("测试数据源");
      vo.setJdbcUrl("jdbc:postgresql://localhost:5432/test");
      vo.setUsername("postgres");
      vo.setDialect("postgresql");
      vo.setIsDefault(true);
      when(datasourceService.listAllVO()).thenReturn(List.of(vo));

      mockMvc.perform(get("/generator/datasources"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data[0].id").value("ds-001"))
          .andExpect(jsonPath("$.data[0].name").value("测试数据源"))
          .andExpect(jsonPath("$.data[0].jdbcUrl")
              .value("jdbc:postgresql://localhost:5432/test"))
          .andExpect(jsonPath("$.data[0].dialect").value("postgresql"))
          .andExpect(jsonPath("$.data[0].isDefault").value(true));
    }

    @Test
    @DisplayName("响应 VO 不应出现 password 字段（敏感字段排除）")
    void should_not_expose_password_field() throws Exception {
      GenDatasourceRespVO vo = new GenDatasourceRespVO();
      vo.setId("ds-001");
      vo.setName("测试数据源");
      when(datasourceService.listAllVO()).thenReturn(List.of(vo));

      String body = mockMvc.perform(get("/generator/datasources"))
          .andExpect(status().isOk())
          .andReturn()
          .getResponse()
          .getContentAsString();

      assertThat(body).doesNotContain("password");
    }

    @Test
    @DisplayName("无数据源时应返回空数组")
    void should_return_empty_array_when_no_datasources() throws Exception {
      when(datasourceService.listAllVO()).thenReturn(List.of());

      mockMvc.perform(get("/generator/datasources"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data").isArray())
          .andExpect(jsonPath("$.data.length()").value(0));
    }
  }

  @Nested
  @DisplayName("GET /generator/datasources/default")
  class GetDefault {

    @Test
    @DisplayName("should return 200 when get default succeeds")
    void should_return_200_when_get_default_succeeds() throws Exception {
      GenDatasourceRespVO vo = new GenDatasourceRespVO();
      vo.setId("ds-default");
      when(datasourceService.getDefaultVO()).thenReturn(vo);

      mockMvc.perform(get("/generator/datasources/default"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }

    @Test
    @DisplayName("未配置默认数据源时应返回 200 且无 data 字段")
    void should_return_200_without_data_when_no_default() throws Exception {
      when(datasourceService.getDefaultVO()).thenReturn(null);

      mockMvc.perform(get("/generator/datasources/default"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").doesNotExist());
    }
  }

  @Nested
  @DisplayName("POST /generator/datasources/test")
  class TestConnection {

    @Test
    @DisplayName("should return 200 when test connection succeeds")
    void should_return_200_when_test_connection_succeeds() throws Exception {
      when(datasourceService.testConnection(any(GenDatasource.class))).thenReturn(true);

      mockMvc.perform(post("/generator/datasources/test")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"name\":\"test\",\"jdbcUrl\":\"jdbc:postgresql://localhost:5432/test\","
                  + "\"username\":\"postgres\",\"password\":\"secret\","
                  + "\"dialect\":\"postgresql\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    @DisplayName("连接失败时应返回 200 + data=false（非 5xx）")
    void should_return_200_with_false_when_connection_fails() throws Exception {
      when(datasourceService.testConnection(any(GenDatasource.class))).thenReturn(false);

      mockMvc.perform(post("/generator/datasources/test")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"name\":\"test\","
                  + "\"jdbcUrl\":\"jdbc:postgresql://localhost:5432/unknown\","
                  + "\"username\":\"postgres\",\"password\":\"wrong\","
                  + "\"dialect\":\"postgresql\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value(false));
    }

    @Test
    @DisplayName("应把请求体完整透传给 Service（含 password）")
    void should_pass_full_payload_to_service() throws Exception {
      when(datasourceService.testConnection(any(GenDatasource.class))).thenReturn(true);

      mockMvc.perform(post("/generator/datasources/test")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"name\":\"test\","
                  + "\"jdbcUrl\":\"jdbc:postgresql://localhost:5432/test\","
                  + "\"username\":\"postgres\",\"password\":\"secret\","
                  + "\"dialect\":\"postgresql\"}"))
          .andExpect(status().isOk());

      ArgumentCaptor<GenDatasource> captor = ArgumentCaptor.forClass(GenDatasource.class);
      verify(datasourceService).testConnection(captor.capture());
      assertThat(captor.getValue().getName()).isEqualTo("test");
      assertThat(captor.getValue().getJdbcUrl())
          .isEqualTo("jdbc:postgresql://localhost:5432/test");
      assertThat(captor.getValue().getUsername()).isEqualTo("postgres");
      assertThat(captor.getValue().getPassword()).isEqualTo("secret");
    }

    @Test
    @DisplayName("name 为空应返回 400 且不调用 Service")
    void should_return_400_when_name_blank() throws Exception {
      mockMvc.perform(post("/generator/datasources/test")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"jdbcUrl\":\"jdbc:postgresql://localhost:5432/test\","
                  + "\"username\":\"postgres\",\"password\":\"secret\"}"))
          .andExpect(status().isBadRequest());

      verify(datasourceService, never()).testConnection(any(GenDatasource.class));
    }

    @Test
    @DisplayName("jdbcUrl 为空应返回 400 且不调用 Service")
    void should_return_400_when_jdbc_url_blank() throws Exception {
      mockMvc.perform(post("/generator/datasources/test")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"name\":\"test\",\"username\":\"postgres\","
                  + "\"password\":\"secret\"}"))
          .andExpect(status().isBadRequest());

      verify(datasourceService, never()).testConnection(any(GenDatasource.class));
    }

    @Test
    @DisplayName("请求体非 JSON 应返回 400")
    void should_return_400_for_malformed_body() throws Exception {
      mockMvc.perform(post("/generator/datasources/test")
              .contentType(MediaType.APPLICATION_JSON)
              .content("not-a-json"))
          .andExpect(status().isBadRequest());

      verify(datasourceService, never()).testConnection(any(GenDatasource.class));
    }
  }

  @Nested
  @DisplayName("POST /generator/datasources")
  class Create {

    @Test
    @DisplayName("should return 200 when create succeeds")
    void should_return_200_when_create_succeeds() throws Exception {
      GenDatasourceRespVO vo = new GenDatasourceRespVO();
      vo.setId("ds-new");
      when(datasourceService.createAndReturnVO(any(GenDatasource.class))).thenReturn(vo);

      mockMvc.perform(post("/generator/datasources")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"name\":\"test\",\"jdbcUrl\":\"jdbc:postgresql://localhost:5432/test\","
                  + "\"username\":\"postgres\",\"password\":\"secret\","
                  + "\"dialect\":\"postgresql\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }

    @Test
    @DisplayName("创建结果应透出 id/name/dialect 字段")
    void should_expose_created_vo_fields() throws Exception {
      GenDatasourceRespVO vo = new GenDatasourceRespVO();
      vo.setId("ds-new");
      vo.setName("test");
      vo.setDialect("postgresql");
      vo.setIsDefault(false);
      when(datasourceService.createAndReturnVO(any(GenDatasource.class))).thenReturn(vo);

      mockMvc.perform(post("/generator/datasources")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"name\":\"test\",\"jdbcUrl\":\"jdbc:postgresql://localhost:5432/test\","
                  + "\"username\":\"postgres\",\"password\":\"secret\","
                  + "\"dialect\":\"postgresql\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.id").value("ds-new"))
          .andExpect(jsonPath("$.data.name").value("test"))
          .andExpect(jsonPath("$.data.dialect").value("postgresql"))
          .andExpect(jsonPath("$.data.isDefault").value(false));
    }

    @Test
    @DisplayName("username 为空应返回 400 且不调用 Service")
    void should_return_400_when_username_blank() throws Exception {
      mockMvc.perform(post("/generator/datasources")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"name\":\"test\","
                  + "\"jdbcUrl\":\"jdbc:postgresql://localhost:5432/test\","
                  + "\"password\":\"secret\"}"))
          .andExpect(status().isBadRequest());

      verify(datasourceService, never()).createAndReturnVO(any(GenDatasource.class));
    }

    @Test
    @DisplayName("name 超长（>64）应返回 400")
    void should_return_400_when_name_too_long() throws Exception {
      String longName = "x".repeat(65);
      mockMvc.perform(post("/generator/datasources")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"name\":\"" + longName + "\","
                  + "\"jdbcUrl\":\"jdbc:postgresql://localhost:5432/test\","
                  + "\"username\":\"postgres\",\"password\":\"secret\"}"))
          .andExpect(status().isBadRequest());

      verify(datasourceService, never()).createAndReturnVO(any(GenDatasource.class));
    }
  }

  @Nested
  @DisplayName("POST /generator/datasources/update")
  class Update {

    @Test
    @DisplayName("应返回 200 并透出更新后的 VO")
    void should_return_200_with_updated_vo() throws Exception {
      GenDatasourceRespVO vo = new GenDatasourceRespVO();
      vo.setId("ds-001");
      vo.setName("改名后");
      when(datasourceService.updateAndReturnVO(any(GenDatasource.class))).thenReturn(vo);

      mockMvc.perform(post("/generator/datasources/update")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"id\":\"ds-001\",\"name\":\"改名后\","
                  + "\"jdbcUrl\":\"jdbc:postgresql://localhost:5432/test\","
                  + "\"username\":\"postgres\",\"dialect\":\"postgresql\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.id").value("ds-001"))
          .andExpect(jsonPath("$.data.name").value("改名后"));
    }

    @Test
    @DisplayName("密码不传时应保留原密码（Controller 不做强制校验）")
    void should_allow_update_without_password() throws Exception {
      when(datasourceService.updateAndReturnVO(any(GenDatasource.class)))
          .thenReturn(new GenDatasourceRespVO());

      mockMvc.perform(post("/generator/datasources/update")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"id\":\"ds-001\",\"name\":\"test\","
                  + "\"jdbcUrl\":\"jdbc:postgresql://localhost:5432/test\","
                  + "\"username\":\"postgres\"}"))
          .andExpect(status().isOk());

      ArgumentCaptor<GenDatasource> captor = ArgumentCaptor.forClass(GenDatasource.class);
      verify(datasourceService).updateAndReturnVO(captor.capture());
      assertThat(captor.getValue().getPassword()).isNull();
    }

    @Test
    @DisplayName("缺少必填字段应返回 400 且不调用 Service")
    void should_return_400_when_required_fields_missing() throws Exception {
      mockMvc.perform(post("/generator/datasources/update")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"id\":\"ds-001\"}"))
          .andExpect(status().isBadRequest());

      verify(datasourceService, never()).updateAndReturnVO(any(GenDatasource.class));
    }
  }

  @Nested
  @DisplayName("DELETE /generator/datasources/{id}")
  class Delete {

    @Test
    @DisplayName("should return 200 when delete succeeds")
    void should_return_200_when_delete_succeeds() throws Exception {
      mockMvc.perform(delete("/generator/datasources/{id}", "ds-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      verify(datasourceService).deleteById("ds-001");
    }

    @Test
    @DisplayName("删除成功时 data 为 null 不出现字段")
    void should_return_null_data_on_delete() throws Exception {
      mockMvc.perform(delete("/generator/datasources/{id}", "ds-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("删除成功时不应调用任何返回值路径（void 语义）")
    void should_invoke_delete_without_return_value() throws Exception {
      mockMvc.perform(delete("/generator/datasources/{id}", "ds-404"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      verify(datasourceService).deleteById("ds-404");
    }
  }

  @Nested
  @DisplayName("异常信封（MvcExceptionHandler 已装配）")
  class ErrorEnvelope {

    private MockMvc adviceMockMvc;

    @BeforeEach
    void setUpAdvice() {
      adviceMockMvc =
          standaloneSetupWithAdvice(
              new MvcExceptionHandler(
                  new MockEnvironment(),
                  null,
                  absentProvider(),
                  absentProvider(),
                  absentProvider()),
              controller);
    }

    @Test
    @DisplayName("Service 抛 BusinessException 应透传错误码 + HTTP 状态")
    void should_expose_business_error_code() throws Exception {
      when(datasourceService.getDefaultVO())
          .thenThrow(BusinessException.of(CoreExceptionCode.PARAM_ERROR));

      adviceMockMvc.perform(get("/generator/datasources/default"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("A01052"));
    }

    @Test
    @DisplayName("Service 抛系统异常应返回 500 + 错误码")
    void should_expose_system_error_code() throws Exception {
      when(datasourceService.testConnection(any(GenDatasource.class)))
          .thenThrow(BusinessException.of(CoreExceptionCode.SYSTEM_ERROR));

      adviceMockMvc.perform(post("/generator/datasources/test")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"name\":\"test\","
                  + "\"jdbcUrl\":\"jdbc:postgresql://localhost:5432/test\","
                  + "\"username\":\"postgres\",\"password\":\"secret\"}"))
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.code").value("B01052"));
    }

    @Test
    @DisplayName("查询全部数据源抛业务异常应透传 400")
    void should_expose_error_on_list() throws Exception {
      when(datasourceService.listAllVO())
          .thenThrow(BusinessException.of(CoreExceptionCode.PARAM_ERROR));

      adviceMockMvc.perform(get("/generator/datasources"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("A01052"));
    }
  }

  /**
   * "容器里没有这个 bean" 的 ObjectProvider，用于脱离 Spring 容器手工装配 advice。
   *
   * @param <T> Provider 承载的 bean 类型
   * @return 取不到任何 bean 的 provider
   */
  private static <T> org.springframework.beans.factory.ObjectProvider<T> absentProvider() {
    return new org.springframework.beans.factory.ObjectProvider<>() {
      @Override
      public T getIfAvailable() {
        return null;
      }

      @Override
      public T getIfUnique() {
        return null;
      }
    };
  }
}
