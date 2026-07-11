package com.njydsz.pmis.project.controller.opportunity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.project.dto.opportunity.OpportunityCreateDTO;
import com.njydsz.pmis.project.dto.opportunity.OpportunityUpdateDTO;
import com.njydsz.pmis.project.entity.opportunity.OpportunityDO;
import com.njydsz.pmis.project.service.opportunity.OpportunityService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * {@link OpportunityController} WebMvc 切片测试
 *
 * <p>验证商机 CRUD 接口的路由映射、入参校验与响应格式。
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@WebMvcTest(OpportunityController.class)
@DisplayName("OpportunityController WebMvc 切片测试")
class OpportunityControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OpportunityService service;

    private static final String BASE_URL = "/opportunity";

    @Nested
    @DisplayName("POST /opportunity - 创建商机")
    class CreateOpportunity {

        @Test
        @DisplayName("合法参数返回 200 + 商机 ID")
        void shouldReturnIdWhenValidInput() throws Exception {
            var dto = new OpportunityCreateDTO();
            dto.setOpportunityCode("OPP-2026-001");
            dto.setOpportunityName("某政企数字化转型项目");
            dto.setCustomerId("CU001");
            dto.setOwnerId("U001");
            dto.setLevel("B");
            dto.setEstimatedAmount(new BigDecimal("500000"));
            when(service.create(any(OpportunityCreateDTO.class))).thenReturn("OPP-001");

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").value("OPP-001"));
        }

        @Test
        @DisplayName("缺少必填字段 opportunityCode 时返回 400")
        void shouldReturn400WhenMissingCode() throws Exception {
            var dto = new OpportunityCreateDTO();
            dto.setOpportunityName("测试商机");
            dto.setCustomerId("CU001");
            dto.setOwnerId("U001");

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("缺少必填字段 customerId 时返回 400")
        void shouldReturn400WhenMissingCustomerId() throws Exception {
            var dto = new OpportunityCreateDTO();
            dto.setOpportunityCode("OPP-001");
            dto.setOpportunityName("测试商机");
            dto.setOwnerId("U001");

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("缺少必填字段 ownerId 时返回 400")
        void shouldReturn400WhenMissingOwnerId() throws Exception {
            var dto = new OpportunityCreateDTO();
            dto.setOpportunityCode("OPP-001");
            dto.setOpportunityName("测试商机");
            dto.setCustomerId("CU001");

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("PUT /opportunity/{id} - 更新商机")
    class UpdateOpportunity {

        @Test
        @DisplayName("合法更新返回成功")
        void shouldReturnOkWhenUpdate() throws Exception {
            var dto = new OpportunityUpdateDTO();
            dto.setOpportunityName("更新后的商机名称");
            doNothing().when(service).update(any(OpportunityUpdateDTO.class));

            mockMvc.perform(put(BASE_URL + "/OPP-001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));

            verify(service).update(any(OpportunityUpdateDTO.class));
        }
    }

    @Nested
    @DisplayName("GET /opportunity/page - 分页查询")
    class PageOpportunity {

        @Test
        @DisplayName("默认参数分页返回结果")
        void shouldReturnPageWithDefaultParams() throws Exception {
            var page = new Page<OpportunityDO>(1, 20);
            page.setTotal(0);
            when(service.page(eq(1), eq(20), isNull(), isNull(), isNull(), isNull()))
                    .thenReturn(page);

            mockMvc.perform(get(BASE_URL + "/page"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.current").value(1));
        }

        @Test
        @DisplayName("page=0 返回 400")
        void shouldReturn400WhenPageIsZero() throws Exception {
            mockMvc.perform(get(BASE_URL + "/page")
                            .param("page", "0"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("带过滤条件正常返回")
        void shouldReturnPageWithFilters() throws Exception {
            var page = new Page<OpportunityDO>(1, 10);
            page.setTotal(2);
            var opp = new OpportunityDO();
            opp.setId("OPP-001");
            opp.setOpportunityName("测试商机");
            page.setRecords(List.of(opp));
            when(service.page(eq(1), eq(10), eq("测试"), eq("NEW"), eq("B"), eq("U001")))
                    .thenReturn(page);

            mockMvc.perform(get(BASE_URL + "/page")
                            .param("page", "1")
                            .param("size", "10")
                            .param("keyword", "测试")
                            .param("status", "NEW")
                            .param("level", "B")
                            .param("ownerId", "U001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.total").value(2))
                    .andExpect(jsonPath("$.data.records[0].id").value("OPP-001"));
        }
    }

    @Nested
    @DisplayName("GET /opportunity/{id} - 查询详情")
    class GetOpportunity {

        @Test
        @DisplayName("存在时返回商机数据")
        void shouldReturnOpportunityWhenExists() throws Exception {
            var opp = new OpportunityDO();
            opp.setId("OPP-001");
            opp.setOpportunityCode("OPP-2026-001");
            opp.setOpportunityName("测试商机");
            when(service.getById("OPP-001")).thenReturn(opp);

            mockMvc.perform(get(BASE_URL + "/OPP-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value("OPP-001"))
                    .andExpect(jsonPath("$.data.opportunityCode").value("OPP-2026-001"));
        }
    }

    @Nested
    @DisplayName("DELETE /opportunity/{id} - 删除商机")
    class DeleteOpportunity {

        @Test
        @DisplayName("合法 ID 删除返回成功")
        void shouldReturnOkWhenDelete() throws Exception {
            doNothing().when(service).delete("OPP-001");

            mockMvc.perform(delete(BASE_URL + "/OPP-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }
    }
}
