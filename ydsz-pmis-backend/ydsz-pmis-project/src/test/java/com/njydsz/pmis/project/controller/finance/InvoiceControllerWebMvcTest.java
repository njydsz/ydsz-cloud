package com.njydsz.pmis.project.controller.finance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.njydsz.pmis.project.dto.finance.InvoiceApprovalDTO;
import com.njydsz.pmis.project.dto.finance.InvoiceCreateDTO;
import com.njydsz.pmis.project.entity.finance.InvoiceDO;
import com.njydsz.pmis.project.service.finance.InvoiceService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * {@link InvoiceController} WebMvc 切片测试
 *
 * <p>验证 HTTP 层行为：路由映射、入参校验、响应格式、Service 调用传参。
 * 不加载数据库/缓存等基础设施，Service 以 Mock 对象注入。
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@WebMvcTest(InvoiceController.class)
@DisplayName("InvoiceController WebMvc 切片测试")
class InvoiceControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private InvoiceService service;

    private static final String BASE_URL = "/finance/invoice";

    // ==================== 创建发票 ====================

    @Nested
    @DisplayName("POST /finance/invoice - 创建发票")
    class CreateInvoice {

        @Test
        @DisplayName("合法参数返回 200 + 成功响应 + 新建 ID")
        void shouldReturn201WithIdWhenValidInput() throws Exception {
            // Given
            var dto = new InvoiceCreateDTO();
            dto.setInvoiceCode("INV-2026-001");
            dto.setInvoiceType("NORMAL");
            dto.setContractId("C001");
            dto.setInitiationId("P001");
            dto.setCustomerId("CU001");
            dto.setInvoiceBasis("MILESTONE");
            dto.setAmount(new BigDecimal("100000.00"));
            when(service.create(any(InvoiceCreateDTO.class))).thenReturn("INV-001");

            // When & Then
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").value("INV-001"));
        }

        @Test
        @DisplayName("缺少必填字段 invoiceCode 时返回校验错误")
        void shouldReturnValidationErrorWhenMissingInvoiceCode() throws Exception {
            var dto = new InvoiceCreateDTO();
            dto.setInvoiceType("NORMAL");
            dto.setContractId("C001");
            dto.setInitiationId("P001");
            dto.setCustomerId("CU001");
            dto.setInvoiceBasis("MILESTONE");
            dto.setAmount(new BigDecimal("100000.00"));

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("缺少必填字段 amount 时返回校验错误")
        void shouldReturnValidationErrorWhenMissingAmount() throws Exception {
            var dto = new InvoiceCreateDTO();
            dto.setInvoiceCode("INV-2026-001");
            dto.setInvoiceType("NORMAL");
            dto.setContractId("C001");
            dto.setInitiationId("P001");
            dto.setCustomerId("CU001");
            dto.setInvoiceBasis("MILESTONE");

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("空请求体返回 400")
        void shouldReturn400WhenEmptyBody() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ==================== 提交审批 ====================

    @Nested
    @DisplayName("PUT /finance/invoice/{id}/submit - 提交审批")
    class SubmitInvoice {

        @Test
        @DisplayName("合法 ID + operatorId 返回成功")
        void shouldReturnOkWhenSubmitWithValidParams() throws Exception {
            doNothing().when(service).submit(eq("INV-001"), eq("U001"));

            mockMvc.perform(put(BASE_URL + "/INV-001/submit")
                            .param("operatorId", "U001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));

            verify(service).submit("INV-001", "U001");
        }

        @Test
        @DisplayName("缺少 operatorId 参数返回 400")
        void shouldReturn400WhenMissingOperatorId() throws Exception {
            mockMvc.perform(put(BASE_URL + "/INV-001/submit"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ==================== 审批通过/驳回 ====================

    @Nested
    @DisplayName("PUT /finance/invoice/{id}/approve - 审批通过")
    class ApproveInvoice {

        @Test
        @DisplayName("合法审批参数返回成功")
        void shouldReturnOkWhenApproveWithValidDto() throws Exception {
            var dto = new InvoiceApprovalDTO();
            dto.setOperatorId("U001");
            dto.setComment("同意");
            doNothing().when(service).approve(eq("INV-001"), any(InvoiceApprovalDTO.class));

            mockMvc.perform(put(BASE_URL + "/INV-001/approve")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));

            verify(service).approve(eq("INV-001"), any(InvoiceApprovalDTO.class));
        }

        @Test
        @DisplayName("空请求体返回 400")
        void shouldReturn400WhenEmptyBody() throws Exception {
            mockMvc.perform(put(BASE_URL + "/INV-001/approve")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("PUT /finance/invoice/{id}/reject - 审批驳回")
    class RejectInvoice {

        @Test
        @DisplayName("合法驳回参数返回成功")
        void shouldReturnOkWhenRejectWithValidDto() throws Exception {
            var dto = new InvoiceApprovalDTO();
            dto.setOperatorId("U001");
            dto.setComment("金额不符");
            doNothing().when(service).reject(eq("INV-001"), any(InvoiceApprovalDTO.class));

            mockMvc.perform(put(BASE_URL + "/INV-001/reject")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));

            verify(service).reject(eq("INV-001"), any(InvoiceApprovalDTO.class));
        }
    }

    // ==================== 红冲/取消 ====================

    @Nested
    @DisplayName("PUT /finance/invoice/{id}/reverse - 红冲")
    class RedReverseInvoice {

        @Test
        @DisplayName("带备注红冲返回成功")
        void shouldReturnOkWhenReverseWithComment() throws Exception {
            doNothing().when(service).redReverse(eq("INV-001"), eq("U001"), eq("开错"));

            mockMvc.perform(put(BASE_URL + "/INV-001/reverse")
                            .param("operatorId", "U001")
                            .param("comment", "开错"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));

            verify(service).redReverse("INV-001", "U001", "开错");
        }

        @Test
        @DisplayName("不带备注红冲返回成功（comment 可选）")
        void shouldReturnOkWhenReverseWithoutComment() throws Exception {
            doNothing().when(service).redReverse(eq("INV-001"), eq("U001"), isNull());

            mockMvc.perform(put(BASE_URL + "/INV-001/reverse")
                            .param("operatorId", "U001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }
    }

    @Nested
    @DisplayName("PUT /finance/invoice/{id}/cancel - 取消")
    class CancelInvoice {

        @Test
        @DisplayName("合法取消返回成功")
        void shouldReturnOkWhenCancel() throws Exception {
            doNothing().when(service).cancel(eq("INV-001"), eq("U001"), anyString());

            mockMvc.perform(put(BASE_URL + "/INV-001/cancel")
                            .param("operatorId", "U001")
                            .param("comment", "客户取消"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));

            verify(service).cancel("INV-001", "U001", "客户取消");
        }
    }

    // ==================== 删除 ====================

    @Nested
    @DisplayName("DELETE /finance/invoice/{id} - 删除")
    class DeleteInvoice {

        @Test
        @DisplayName("合法 ID 删除返回成功")
        void shouldReturnOkWhenDelete() throws Exception {
            doNothing().when(service).delete("INV-001");

            mockMvc.perform(delete(BASE_URL + "/INV-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));

            verify(service).delete("INV-001");
        }
    }

    // ==================== 查询详情 ====================

    @Nested
    @DisplayName("GET /finance/invoice/{id} - 查询详情")
    class GetInvoice {

        @Test
        @DisplayName("存在时返回 200 + 发票数据")
        void shouldReturnInvoiceWhenExists() throws Exception {
            var invoice = new InvoiceDO();
            invoice.setId("INV-001");
            invoice.setInvoiceCode("INV-2026-001");
            invoice.setAmount(new BigDecimal("100000.00"));
            when(service.getById("INV-001")).thenReturn(invoice);

            mockMvc.perform(get(BASE_URL + "/INV-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.id").value("INV-001"))
                    .andExpect(jsonPath("$.data.invoiceCode").value("INV-2026-001"));
        }

        @Test
        @DisplayName("不存在时返回 200 + null data（由全局异常处理器决定）")
        void shouldReturnNullWhenNotExists() throws Exception {
            when(service.getById("NOT-EXIST")).thenReturn(null);

            mockMvc.perform(get(BASE_URL + "/NOT-EXIST"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }
    }

    // ==================== 分页查询 ====================

    @Nested
    @DisplayName("GET /finance/invoice/page - 分页查询")
    class PageInvoice {

        @Test
        @DisplayName("默认分页参数返回分页结果")
        void shouldReturnPageWithDefaultParams() throws Exception {
            var page = new Page<InvoiceDO>(1, 20);
            page.setTotal(0);
            when(service.page(eq(1), eq(20), isNull(), isNull(), isNull(), isNull(), isNull(), isNull()))
                    .thenReturn(page);

            mockMvc.perform(get(BASE_URL + "/page"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.current").value(1));
        }

        @Test
        @DisplayName("page=0 返回 400（最小值 1）")
        void shouldReturn400WhenPageIsZero() throws Exception {
            mockMvc.perform(get(BASE_URL + "/page")
                            .param("page", "0"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("size=200 返回 400（最大值 100）")
        void shouldReturn400WhenSizeExceeds100() throws Exception {
            mockMvc.perform(get(BASE_URL + "/page")
                            .param("size", "200"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("带全部过滤参数正常返回")
        void shouldReturnPageWithAllFilters() throws Exception {
            var page = new Page<InvoiceDO>(1, 10);
            page.setTotal(1);
            var invoice = new InvoiceDO();
            invoice.setId("INV-001");
            page.setRecords(List.of(invoice));
            when(service.page(eq(1), eq(10), eq("测试"), eq("ISSUED"),
                    eq("C001"), eq("P001"), eq("CU001"), eq("NORMAL")))
                    .thenReturn(page);

            mockMvc.perform(get(BASE_URL + "/page")
                            .param("page", "1")
                            .param("size", "10")
                            .param("keyword", "测试")
                            .param("status", "ISSUED")
                            .param("contractId", "C001")
                            .param("initiationId", "P001")
                            .param("customerId", "CU001")
                            .param("invoiceType", "NORMAL"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.total").value(1));
        }
    }

    // ==================== 按合同汇总 ====================

    @Nested
    @DisplayName("GET /finance/invoice/sum/byContract - 按合同汇总开票金额")
    class SumByContract {

        @Test
        @DisplayName("返回已开票金额")
        void shouldReturnSumAmount() throws Exception {
            when(service.sumInvoicedByContract("C001"))
                    .thenReturn(new BigDecimal("500000.00"));

            mockMvc.perform(get(BASE_URL + "/sum/byContract")
                            .param("contractId", "C001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").value(500000.00));
        }
    }

    // ==================== 按状态分组台账 ====================

    @Nested
    @DisplayName("GET /finance/invoice/aggregate/byStatus - 按状态分组台账")
    class AggregateByStatus {

        @Test
        @DisplayName("返回各状态发票汇总")
        void shouldReturnAggregatedData() throws Exception {
            List<Map<String, Object>> data = List.of(
                    Map.<String, Object>of("status", "ISSUED", "count", 3, "amount", new BigDecimal("300000")),
                    Map.<String, Object>of("status", "DRAFT", "count", 1, "amount", new BigDecimal("50000"))
            );
            when(service.aggregateByStatus("C001")).thenReturn(data);

            mockMvc.perform(get(BASE_URL + "/aggregate/byStatus")
                            .param("contractId", "C001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].status").value("ISSUED"));
        }
    }
}
