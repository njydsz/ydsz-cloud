package com.njydsz.pmis.execution.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AfterSalesCodeGen 业务编码生成器测试")
class AfterSalesCodeGenTest {

    private static final Pattern WY = Pattern.compile("^WY-\\d{8}-\\d{4}$");
    private static final Pattern TK = Pattern.compile("^TK-\\d{14}-\\d{4}$");
    private static final Pattern SV = Pattern.compile("^SV-\\d{8}-\\d{4}$");

    @Test
    @DisplayName("warrantyCode 格式 WY-yyyyMMdd-XXXX")
    void warrantyCode() {
        String c = AfterSalesCodeGen.warrantyCode(LocalDate.of(2026, 7, 1));
        assertThat(c).matches(WY);
        assertThat(c).startsWith("WY-20260701-");
    }

    @Test
    @DisplayName("warrantyCode 默认日期 = 今天")
    void warrantyCode_default() {
        String c = AfterSalesCodeGen.warrantyCode(null);
        assertThat(c).matches(WY);
    }

    @Test
    @DisplayName("ticketCode 格式 TK-yyyyMMddHHmmss-XXXX")
    void ticketCode() {
        String c = AfterSalesCodeGen.ticketCode(LocalDateTime.of(2026, 7, 1, 9, 0, 0));
        assertThat(c).matches(TK);
        assertThat(c).startsWith("TK-20260701090000-");
    }

    @Test
    @DisplayName("ticketCode 默认时间 = now")
    void ticketCode_default() {
        String c = AfterSalesCodeGen.ticketCode(null);
        assertThat(c).matches(TK);
    }

    @Test
    @DisplayName("surveyCode 格式 SV-yyyyMMdd-XXXX")
    void surveyCode() {
        String c = AfterSalesCodeGen.surveyCode(LocalDate.of(2026, 7, 1));
        assertThat(c).matches(SV);
        assertThat(c).startsWith("SV-20260701-");
    }

    @Test
    @DisplayName("多次生成互不相同（随机后缀）")
    void unique() {
        String a = AfterSalesCodeGen.ticketCode(null);
        String b = AfterSalesCodeGen.ticketCode(null);
        // 不保证 100% 不重复，但概率极低
        // 至少 100 次内大概率都不同
        boolean anyDiff = false;
        for (int i = 0; i < 5; i++) {
            if (!AfterSalesCodeGen.ticketCode(null).equals(a)) {
                anyDiff = true;
                break;
            }
        }
        assertThat(anyDiff).isTrue();
        assertThat(a).isNotEqualTo(b);
    }
}
