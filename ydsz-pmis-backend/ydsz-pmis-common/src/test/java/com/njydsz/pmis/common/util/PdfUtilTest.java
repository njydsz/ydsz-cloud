package com.njydsz.pmis.common.util;

import com.lowagie.text.pdf.PdfReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PdfUtil 单元测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("PdfUtil PDF 生成测试")
class PdfUtilTest {

    @Test
    @DisplayName("buildSimpleReport 生成可被 PdfReader 解析的有效 PDF")
    void buildSimpleReport_valid() throws Exception {
        Map<String, String> fields = PdfUtil.kv();
        fields.put("合同编号", "PMIS-2026-001");
        fields.put("客户名称", "测试客户有限公司");
        fields.put("签订日期", "2026-07-01");

        List<PdfUtil.Section> sections = List.of(
                new PdfUtil.Section("第一条 合作范围", "甲乙双方就 PMIS 系统部署达成合作。"),
                new PdfUtil.Section("第二条 合同金额", "合同总金额：人民币 1,000,000.00 元。")
        );

        byte[] pdf = PdfUtil.buildSimpleReport("PMIS 服务合同", fields, sections);
        assertThat(pdf).isNotNull();
        assertThat(pdf.length).isGreaterThan(1000);
        // PDF 魔数
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");

        // 用 PdfReader 解析
        try (PdfReader reader = new PdfReader(new ByteArrayInputStream(pdf))) {
            assertThat(reader.getNumberOfPages()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("buildSimpleReport 字段为空时仍能生成 PDF")
    void buildSimpleReport_emptyFields() throws Exception {
        byte[] pdf = PdfUtil.buildSimpleReport("空报告", null, null);
        assertThat(pdf).isNotNull();
        assertThat(pdf.length).isGreaterThan(500);
    }

    @Test
    @DisplayName("buildTableReport 生成多列表格 PDF")
    void buildTableReport_valid() throws Exception {
        List<String> headers = List.of("项目编号", "项目名称", "合同金额", "已确认收入", "毛利率");
        List<List<String>> rows = List.of(
                List.of("PRJ-001", "PMIS 一期", "1,000,000.00", "600,000.00", "25.5%"),
                List.of("PRJ-002", "PMIS 二期", "2,000,000.00", "1,500,000.00", "30.2%"),
                List.of("PRJ-003", "运维服务",   "500,000.00",   "400,000.00",   "45.0%")
        );

        byte[] pdf = PdfUtil.buildTableReport("项目利润表", headers, rows);
        assertThat(pdf).isNotNull();
        assertThat(pdf.length).isGreaterThan(1000);

        try (PdfReader reader = new PdfReader(new ByteArrayInputStream(pdf))) {
            assertThat(reader.getNumberOfPages()).isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    @DisplayName("buildTableReport 表头为空抛 IllegalArgumentException")
    void buildTableReport_emptyHeaders() {
        assertThatThrownBy(() -> PdfUtil.buildTableReport("测试", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PdfUtil.buildTableReport("测试", List.of(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("kv() 返回 LinkedHashMap 保持插入顺序")
    void kv_preservesOrder() {
        Map<String, String> m = PdfUtil.kv();
        m.put("b", "2");
        m.put("a", "1");
        m.put("c", "3");
        assertThat(m.keySet()).containsExactly("b", "a", "c");
    }
}
