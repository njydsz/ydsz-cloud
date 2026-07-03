package com.njydsz.pmis.project.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("售后业务编码生成器测试")
class AfterSalesCodeGenTest {

    @Test
    @DisplayName("生成质保单编码 - 格式为 WY-yyyyMMdd-XXXX")
    void shouldGenerateWarrantyCodeWithCorrectFormat() {
        LocalDate today = LocalDate.of(2026, 7, 1);
        String code = AfterSalesCodeGen.warrantyCode(today);
        assertNotNull(code);
        assertTrue(code.matches("WY-20260701-\\d{4}"),
                "质保单编码格式应为 WY-yyyyMMdd-XXXX，实际：" + code);
    }

    @Test
    @DisplayName("生成运维工单编码 - 格式为 TK-yyyyMMddHHmmss-XXXX")
    void shouldGenerateTicketCodeWithCorrectFormat() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 1, 15, 30, 45);
        String code = AfterSalesCodeGen.ticketCode(now);
        assertNotNull(code);
        assertTrue(code.matches("TK-20260701153045-\\d{4}"),
                "工单编码格式应为 TK-yyyyMMddHHmmss-XXXX，实际：" + code);
    }

    @Test
    @DisplayName("生成满意度调查编码 - 格式为 SV-yyyyMMdd-XXXX")
    void shouldGenerateSurveyCodeWithCorrectFormat() {
        LocalDate today = LocalDate.of(2026, 7, 1);
        String code = AfterSalesCodeGen.surveyCode(today);
        assertNotNull(code);
        assertTrue(code.matches("SV-20260701-\\d{4}"),
                "调查编码格式应为 SV-yyyyMMdd-XXXX，实际：" + code);
    }

    @Test
    @DisplayName("多次调用生成不同编码 - 验证唯一性")
    void shouldGenerateUniqueCodes() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            String code = AfterSalesCodeGen.warrantyCode(null);
            codes.add(code);
        }
        assertEquals(100, codes.size(), "100 次调用应生成 100 个不同的编码");
    }

    @Test
    @DisplayName("质保单编码 - 传入 null 使用当前日期")
    void shouldUseCurrentDateWhenNull() {
        String code = AfterSalesCodeGen.warrantyCode(null);
        assertNotNull(code);
        assertTrue(code.startsWith("WY-"), "编码应以 WY- 开头");
    }

    @Test
    @DisplayName("工单编码 - 传入 null 使用当前时间")
    void shouldUseCurrentTimeWhenNull() {
        String code = AfterSalesCodeGen.ticketCode(null);
        assertNotNull(code);
        assertTrue(code.startsWith("TK-"), "编码应以 TK- 开头");
    }
}