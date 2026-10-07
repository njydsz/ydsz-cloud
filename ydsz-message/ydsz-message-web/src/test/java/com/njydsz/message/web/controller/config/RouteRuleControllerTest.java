package com.njydsz.message.web.controller.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.message.domain.dto.RouteRuleUpsertDTO;
import com.njydsz.message.domain.vo.MsgRouteRuleVO;
import com.njydsz.message.server.service.config.RouteRuleService;

/**
 * RouteRuleController 集成测试。
 *
 * <p>使用 {@code MockMvcBuilders.standaloneSetup()} 构建轻量级 Web 层测试，通过 Mockito {@code @Mock} 模拟
 * {@link RouteRuleService} 依赖，验证 HTTP 路由、请求参数绑定、响应格式正确性。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@ExtendWith(MockitoExtension.class)
class RouteRuleControllerTest {

  private MockMvc mockMvc;

  @Mock
  private RouteRuleService routeRuleService;

  @InjectMocks
  private RouteRuleController routeRuleController;

  private MsgRouteRuleVO sampleRule;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(routeRuleController).build();

    sampleRule = new MsgRouteRuleVO();
    sampleRule.setId("rule-001");
    sampleRule.setRuleCode("SMS_DEFAULT");
    sampleRule.setRuleName("短信默认路由");
    sampleRule.setBizType("VERIFY_CODE");
    sampleRule.setChannel("SMS");
    sampleRule.setPriority(10);
    sampleRule.setConditionExpr("request.bizType == 'VERIFY_CODE'");
    sampleRule.setTargetChannel("SMS");
    sampleRule.setFallbackChannel("EMAIL");
    sampleRule.setDescription("验证码类消息走短信通道");
    sampleRule.setStatus("ENABLED");
    sampleRule.setCreatedAt(LocalDateTime.of(2026, 10, 1, 10, 0));
  }

  @Nested
  @DisplayName("POST /message/route-rule - create")
  class Create {

    @Test
    @DisplayName("创建路由规则成功时返回 200 + 规则 VO")
    void shouldReturnRuleOnSuccess() throws Exception {
      RouteRuleUpsertDTO dto = new RouteRuleUpsertDTO();
      dto.setRuleCode("SMS_DEFAULT");
      dto.setBizType("VERIFY_CODE");
      dto.setChannel("SMS");
      dto.setPriority(10);

      when(routeRuleService.create(any(RouteRuleUpsertDTO.class))).thenReturn(sampleRule);

      mockMvc.perform(post("/message/route-rule")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"ruleCode\":\"SMS_DEFAULT\",\"bizType\":\"VERIFY_CODE\","
                  + "\"channel\":\"SMS\",\"priority\":10}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"))
          .andExpect(jsonPath("$.data.id").value("rule-001"))
          .andExpect(jsonPath("$.data.ruleCode").value("SMS_DEFAULT"))
          .andExpect(jsonPath("$.data.targetChannel").value("SMS"));

      verify(routeRuleService, times(1)).create(any(RouteRuleUpsertDTO.class));
    }
  }

  @Nested
  @DisplayName("PUT /message/route-rule/{id} - update")
  class Update {

    @Test
    @DisplayName("更新路由规则成功时返回 200 + 更新后规则 VO")
    void shouldReturnUpdatedRule() throws Exception {
      MsgRouteRuleVO updated = new MsgRouteRuleVO();
      updated.setId("rule-001");
      updated.setRuleCode("SMS_DEFAULT");
      updated.setPriority(5);
      updated.setTargetChannel("PUSH");

      when(routeRuleService.update(eq("rule-001"), any(RouteRuleUpsertDTO.class))).thenReturn(updated);

      mockMvc.perform(put("/message/route-rule/{id}", "rule-001")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"ruleCode\":\"SMS_DEFAULT\",\"priority\":5,"
                  + "\"targetChannel\":\"PUSH\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"))
          .andExpect(jsonPath("$.data.priority").value(5))
          .andExpect(jsonPath("$.data.targetChannel").value("PUSH"));

      verify(routeRuleService, times(1)).update(eq("rule-001"), any(RouteRuleUpsertDTO.class));
    }
  }

  @Nested
  @DisplayName("DELETE /message/route-rule/{id} - delete")
  class Delete {

    @Test
    @DisplayName("删除路由规则成功时返回 200 + Void")
    void shouldReturnSuccessOnDelete() throws Exception {
      mockMvc.perform(delete("/message/route-rule/{id}", "rule-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"));

      verify(routeRuleService, times(1)).delete("rule-001");
    }
  }

  @Nested
  @DisplayName("GET /message/route-rule/{id} - getById")
  class GetById {

    @Test
    @DisplayName("规则存在时返回 200 + MsgRouteRuleVO")
    void shouldReturnRuleWhenExists() throws Exception {
      when(routeRuleService.getById("rule-001")).thenReturn(sampleRule);

      mockMvc.perform(get("/message/route-rule/{id}", "rule-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"))
          .andExpect(jsonPath("$.data.id").value("rule-001"))
          .andExpect(jsonPath("$.data.ruleCode").value("SMS_DEFAULT"))
          .andExpect(jsonPath("$.data.conditionExpr").value("request.bizType == 'VERIFY_CODE'"))
          .andExpect(jsonPath("$.data.status").value("ENABLED"));

      verify(routeRuleService, times(1)).getById("rule-001");
    }

    @Test
    @DisplayName("规则不存在时返回 200 + null data")
    void shouldReturnNullWhenRuleNotFound() throws Exception {
      when(routeRuleService.getById("rule-404")).thenReturn(null);

      mockMvc.perform(get("/message/route-rule/{id}", "rule-404"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"));

      verify(routeRuleService, times(1)).getById("rule-404");
    }
  }

  @Nested
  @DisplayName("GET /message/route-rule/page - page")
  class Page {

    @Test
    @DisplayName("分页查询返回 PageResponse 包装数据")
    void shouldReturnPagedResult() throws Exception {
      @SuppressWarnings("unchecked")
      PageResponse<List<MsgRouteRuleVO>> pageResult = new PageResponse<>();
      pageResult.setData(List.of(sampleRule));
      pageResult.setTotal(1L);
      pageResult.setPageNum(1L);
      pageResult.setPageSize(20L);

      when(routeRuleService.page(any())).thenReturn(pageResult);

      mockMvc.perform(get("/message/route-rule/page")
              .param("pageNum", "1")
              .param("pageSize", "20"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"))
          .andExpect(jsonPath("$.data.total").value(1))
          .andExpect(jsonPath("$.data.data[0].id").value("rule-001"));

      verify(routeRuleService, times(1)).page(any());
    }
  }

  @Nested
  @DisplayName("GET /message/route-rule/enabled - listEnabled")
  class ListEnabled {

    @Test
    @DisplayName("查询启用规则列表返回 200 + 列表数据")
    void shouldReturnEnabledRules() throws Exception {
      when(routeRuleService.listEnabled()).thenReturn(List.of(sampleRule));

      mockMvc.perform(get("/message/route-rule/enabled"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"))
          .andExpect(jsonPath("$.data[0].id").value("rule-001"))
          .andExpect(jsonPath("$.data[0].status").value("ENABLED"));

      verify(routeRuleService, times(1)).listEnabled();
    }

    @Test
    @DisplayName("无启用规则时返回空列表")
    void shouldReturnEmptyListWhenNoEnabledRules() throws Exception {
      when(routeRuleService.listEnabled()).thenReturn(List.of());

      mockMvc.perform(get("/message/route-rule/enabled"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"))
          .andExpect(jsonPath("$.data").isArray());

      verify(routeRuleService, times(1)).listEnabled();
    }
  }
}
