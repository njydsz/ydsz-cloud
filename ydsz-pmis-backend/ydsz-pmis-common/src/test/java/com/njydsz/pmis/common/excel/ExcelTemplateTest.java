package com.njydsz.pmis.common.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ExcelTemplate 单元测试（与 execution 模块解耦，DTO 内部定义）
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("ExcelTemplate 模板生成测试")
class ExcelTemplateTest {

    /** 简单测试 DTO（避免依赖业务模块） */
    @Data
    public static class DemoRow {
        @ExcelProperty("编码")
        private String code;
        @ExcelProperty("名称")
        private String name;
        @ExcelProperty(value = "金额")
        private BigDecimal amount;
    }

    @Test
    @DisplayName("build 必传 head class")
    void build_mustHaveHead() {
        assertThatThrownBy(() -> ExcelTemplate.builder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("head class is required");
    }

    @Test
    @DisplayName("build 最小调用（仅 head）应返回非空字节数组")
    void build_minimal() {
        byte[] bytes = ExcelTemplate.builder()
                .head(DemoRow.class)
                .build();
        assertThat(bytes).isNotNull();
        assertThat(bytes.length).isGreaterThan(1000);  // xlsx 文件头 + 表头 + 样式
    }

    @Test
    @DisplayName("build 带样例数据可正常生成")
    void build_withSample() {
        DemoRow row = new DemoRow();
        row.setCode("DEMO-001");
        row.setName("测试");
        row.setAmount(new BigDecimal("100.00"));

        byte[] bytes = ExcelTemplate.builder()
                .head(DemoRow.class)
                .sampleData(List.of(row))
                .sheetName("测试页")
                .build();
        assertThat(bytes).isNotNull();
        assertThat(bytes.length).isGreaterThan(1000);
    }

    @Test
    @DisplayName("buildTo 写入到流，OutputStream 内容长度与 build 接近")
    void buildTo_consistent() throws Exception {
        byte[] direct = ExcelTemplate.builder()
                .head(DemoRow.class)
                .build();

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        ExcelTemplate.builder()
                .head(DemoRow.class)
                .buildTo(stream);

        // 允许 ±50 字节差异（样式器内部时间戳）
        assertThat(Math.abs(direct.length - stream.size())).isLessThan(50);
    }

    @Test
    @DisplayName("addRequiredMark 不影响构建")
    void requiredMark_doesNotFail() {
        byte[] bytes = ExcelTemplate.builder()
                .head(DemoRow.class)
                .addRequiredMark("code", "amount")
                .addRequiredMark("name")
                .build();
        assertThat(bytes).isNotNull();
    }

    @Test
    @DisplayName("sampleData 为 null 时使用空列表")
    void nullSampleData_safe() {
        byte[] bytes = ExcelTemplate.builder()
                .head(DemoRow.class)
                .sampleData(null)
                .build();
        assertThat(bytes).isNotNull();
    }
}
