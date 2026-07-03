package com.njydsz.pmis.project.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("预警编码生成器测试")
class AlertCodeGenTest {

    @Test
    @DisplayName("生成预警编码 - 格式为 ALT-{LEVEL}-{TYPE}-{YYYYMMDD}-{HHmmssSSS}-{4位随机}-{2位序列}")
    void shouldGenerateAlertCodeWithCorrectFormat() {
        String code = AlertCodeGen.next("BUDGET", "RED");
        assertNotNull(code);
        assertTrue(code.matches("ALT-RED-BUDGET-\\d{8}-\\d{9}-\\d{4}-\\d{2}"),
                "预警编码格式不符合预期，实际：" + code);
    }

    @Test
    @DisplayName("type 为 null 时使用 GEN 作为默认值")
    void shouldUseGenWhenTypeIsNull() {
        String code = AlertCodeGen.next(null, null);
        assertNotNull(code);
        assertTrue(code.contains("GEN"), "type 为 null 时应使用 GEN，实际：" + code);
    }

    @Test
    @DisplayName("level 为 null 时省略等级部分")
    void shouldOmitLevelWhenNull() {
        String code = AlertCodeGen.next("EVM", null);
        assertNotNull(code);
        assertTrue(code.matches("ALT-EVM-\\d{8}-\\d{9}-\\d{4}-\\d{2}"),
                "level 为 null 时应省略等级，实际：" + code);
    }

    @Test
    @DisplayName("type 和 level 均不为 null 时包含完整前缀")
    void shouldIncludeLevelWhenPresent() {
        String code = AlertCodeGen.next("MARGIN", "YELLOW");
        assertNotNull(code);
        assertTrue(code.startsWith("ALT-YELLOW-MARGIN-"),
                "应包含完整前缀 ALT-YELLOW-MARGIN-，实际：" + code);
    }

    @Test
    @DisplayName("多次调用生成不同编码 - 验证唯一性（毫秒精度+自增序列确保唯一）")
    void shouldGenerateUniqueCodes() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            String code = AlertCodeGen.next("BUDGET", "RED");
            codes.add(code);
        }
        assertEquals(100, codes.size(), "100 次调用应生成 100 个不同的编码，实际：" + codes.size());
    }
}