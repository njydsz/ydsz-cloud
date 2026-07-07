package com.njydsz.pmis.literule.excel;

import com.njydsz.pmis.literule.api.DecisionTableDefinition;
import com.njydsz.pmis.literule.api.HitPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DecisionTableExcelExporter 单元测试（P0-3）
 *
 * <p>验证决策表 Excel 导入导出能力，覆盖：
 * <ol>
 *   <li>导出完整决策表（含多条件列、多动作列、多行、默认动作）</li>
 *   <li>导出空决策表（无行）</li>
 *   <li>导出后导入，验证数据一致性（round-trip）</li>
 *   <li>导入 Excel 模板</li>
 *   <li>导入无效 Excel（格式错误抛异常）</li>
 *   <li>各种 HitPolicy 导出/导入</li>
 *   <li>条件列/动作列类型为 number/string/boolean 的处理</li>
 *   <li>含特殊字符（中文、符号）的条件值</li>
 * </ol>
 *
 * <p>测试风格参考 {@code DefaultRuleEngineTest}，使用 AssertJ 断言。
 *
 * @author ydsz-pmis-team
 */
@DisplayName("DecisionTableExcelExporter 单元测试")
class DecisionTableExcelExporterTest {

    private final DecisionTableExcelExporter exporter = new DecisionTableExcelExporter();

    // ==================== 辅助方法 ====================

    /**
     * 构造完整决策表（含多条件列、多动作列、多行、默认动作）
     */
    private DecisionTableDefinition buildFullTable() {
        return DecisionTableDefinition.builder()
                .tableCode("DT_PROJECT_RISK")
                .tableName("项目风险等级决策表")
                .description("根据 EVM 红灯数和毛利率判定风险等级")
                .category("RISK")
                .hitPolicy(HitPolicy.FIRST)
                .priority(100)
                .scope("PROJECT")
                .conditionColumns(List.of(
                        DecisionTableDefinition.Column.builder()
                                .name("evmRedCount").label("EVM 红灯数").type("number").build(),
                        DecisionTableDefinition.Column.builder()
                                .name("grossMargin").label("毛利率").type("number").build()
                ))
                .actionColumns(List.of(
                        DecisionTableDefinition.Column.builder()
                                .name("severity").label("严重度").type("string").build(),
                        DecisionTableDefinition.Column.builder()
                                .name("title").label("标题").type("string").build()
                ))
                .rows(List.of(
                        DecisionTableDefinition.Row.builder()
                                .conditions(Map.of("evmRedCount", ">=3"))
                                .actions(Map.of("severity", "RED", "title", "EVM 严重偏离"))
                                .build(),
                        DecisionTableDefinition.Row.builder()
                                .conditions(Map.of("grossMargin", "<0.05"))
                                .actions(Map.of("severity", "YELLOW", "title", "毛利率过低"))
                                .build()
                ))
                .defaultActions(buildDefaultActions("INFO", "正常"))
                .build();
    }

    private Map<String, Object> buildDefaultActions(String severity, String title) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("severity", severity);
        map.put("title", title);
        return map;
    }

    // ==================== 导出测试 ====================

    @Nested
    @DisplayName("导出 Excel")
    class ExportTest {

        @Test
        @DisplayName("导出完整决策表 - 返回非空字节数组")
        void shouldExportFullDecisionTable() {
            DecisionTableDefinition def = buildFullTable();

            byte[] bytes = exporter.exportToExcel(def);

            assertThat(bytes).isNotNull();
            assertThat(bytes.length).isGreaterThan(0);
            // xlsx 文件以 PK (0x50 0x4B) 开头（ZIP 格式魔数）
            assertThat(bytes[0]).isEqualTo((byte) 0x50);
            assertThat(bytes[1]).isEqualTo((byte) 0x4B);
        }

        @Test
        @DisplayName("导出空决策表（无行） - 成功导出")
        void shouldExportEmptyRowsTable() {
            DecisionTableDefinition def = DecisionTableDefinition.builder()
                    .tableCode("DT_EMPTY")
                    .tableName("空决策表")
                    .category("TEST")
                    .hitPolicy(HitPolicy.FIRST)
                    .conditionColumns(List.of(
                            DecisionTableDefinition.Column.builder()
                                    .name("c1").label("条件1").type("string").build()))
                    .actionColumns(List.of(
                            DecisionTableDefinition.Column.builder()
                                    .name("a1").label("动作1").type("string").build()))
                    .rows(List.of())
                    .build();

            byte[] bytes = exporter.exportToExcel(def);

            assertThat(bytes).isNotNull();
            assertThat(bytes.length).isGreaterThan(0);
        }

        @Test
        @DisplayName("导出 null 定义 - 抛 RuntimeException")
        void shouldThrowWhenExportNullDefinition() {
            assertThatThrownBy(() -> exporter.exportToExcel(null))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("不能为 null");
        }

        @Test
        @DisplayName("导出含特殊字符的决策表 - 中文/符号正常")
        void shouldExportTableWithSpecialCharacters() {
            DecisionTableDefinition def = DecisionTableDefinition.builder()
                    .tableCode("DT_SPECIAL_中文")
                    .tableName("特殊字符：决策表【测试】")
                    .description("含特殊符号 <>&\"' 测试")
                    .category("测试/分类")
                    .hitPolicy(HitPolicy.COLLECT)
                    .priority(50)
                    .conditionColumns(List.of(
                            DecisionTableDefinition.Column.builder()
                                    .name("字段_中文").label("中文标签【1】").type("string").build()))
                    .actionColumns(List.of(
                            DecisionTableDefinition.Column.builder()
                                    .name("输出").label("输出（结果）").type("string").build()))
                    .rows(List.of(
                            DecisionTableDefinition.Row.builder()
                                    .conditions(Map.of("字段_中文", "值|A|B"))
                                    .actions(Map.of("输出", "结果 < 100 & > 50"))
                                    .build()))
                    .build();

            byte[] bytes = exporter.exportToExcel(def);

            assertThat(bytes).isNotNull();
            assertThat(bytes.length).isGreaterThan(0);
        }
    }

    // ==================== 导入测试 ====================

    @Nested
    @DisplayName("导入 Excel")
    class ImportTest {

        @Test
        @DisplayName("导入空字节数组 - 抛 IllegalArgumentException")
        void shouldThrowWhenImportEmptyBytes() {
            assertThatThrownBy(() -> exporter.importFromExcel(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("不能为空");

            assertThatThrownBy(() -> exporter.importFromExcel(new byte[0]))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("不能为空");
        }

        @Test
        @DisplayName("导入无效 Excel 字节 - 抛 IllegalArgumentException")
        void shouldThrowWhenImportInvalidExcel() {
            byte[] invalid = "not an excel file".getBytes();

            assertThatThrownBy(() -> exporter.importFromExcel(invalid))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ==================== Round-Trip 一致性测试 ====================

    @Nested
    @DisplayName("Round-Trip 一致性")
    class RoundTripTest {

        @Test
        @DisplayName("导出后导入 - 完整决策表数据一致")
        void shouldRoundTripFullTable() {
            DecisionTableDefinition original = buildFullTable();

            byte[] bytes = exporter.exportToExcel(original);
            DecisionTableDefinition restored = exporter.importFromExcel(bytes);

            assertThat(restored).isNotNull();
            assertThat(restored.getTableCode()).isEqualTo(original.getTableCode());
            assertThat(restored.getTableName()).isEqualTo(original.getTableName());
            assertThat(restored.getDescription()).isEqualTo(original.getDescription());
            assertThat(restored.getCategory()).isEqualTo(original.getCategory());
            assertThat(restored.getHitPolicy()).isEqualTo(original.getHitPolicy());
            assertThat(restored.getPriority()).isEqualTo(original.getPriority());
            assertThat(restored.getScope()).isEqualTo(original.getScope());

            // 条件列
            assertThat(restored.getConditionColumns()).hasSize(original.getConditionColumns().size());
            assertThat(restored.getConditionColumns().get(0).getName()).isEqualTo("evmRedCount");
            assertThat(restored.getConditionColumns().get(0).getLabel()).isEqualTo("EVM 红灯数");
            assertThat(restored.getConditionColumns().get(0).getType()).isEqualTo("number");
            assertThat(restored.getConditionColumns().get(1).getName()).isEqualTo("grossMargin");
            assertThat(restored.getConditionColumns().get(1).getLabel()).isEqualTo("毛利率");
            assertThat(restored.getConditionColumns().get(1).getType()).isEqualTo("number");

            // 动作列
            assertThat(restored.getActionColumns()).hasSize(original.getActionColumns().size());
            assertThat(restored.getActionColumns().get(0).getName()).isEqualTo("severity");
            assertThat(restored.getActionColumns().get(0).getType()).isEqualTo("string");
            assertThat(restored.getActionColumns().get(1).getName()).isEqualTo("title");

            // 决策行
            assertThat(restored.getRows()).hasSize(2);
            DecisionTableDefinition.Row row0 = restored.getRows().get(0);
            assertThat(row0.getConditions()).containsEntry("evmRedCount", ">=3");
            assertThat(row0.getActions()).containsEntry("severity", "RED");
            assertThat(row0.getActions()).containsEntry("title", "EVM 严重偏离");

            DecisionTableDefinition.Row row1 = restored.getRows().get(1);
            assertThat(row1.getConditions()).containsEntry("grossMargin", "<0.05");
            assertThat(row1.getActions()).containsEntry("severity", "YELLOW");
            assertThat(row1.getActions()).containsEntry("title", "毛利率过低");

            // 默认动作
            assertThat(restored.getDefaultActions()).isNotNull();
            assertThat(restored.getDefaultActions()).containsEntry("severity", "INFO");
            assertThat(restored.getDefaultActions()).containsEntry("title", "正常");
        }

        @Test
        @DisplayName("导出后导入 - 空决策表（无行）一致")
        void shouldRoundTripEmptyRowsTable() {
            DecisionTableDefinition original = DecisionTableDefinition.builder()
                    .tableCode("DT_EMPTY_RT")
                    .tableName("空决策表-往返")
                    .category("TEST")
                    .hitPolicy(HitPolicy.FIRST)
                    .conditionColumns(List.of(
                            DecisionTableDefinition.Column.builder()
                                    .name("c1").label("条件1").type("string").build()))
                    .actionColumns(List.of(
                            DecisionTableDefinition.Column.builder()
                                    .name("a1").label("动作1").type("string").build()))
                    .rows(List.of())
                    .build();

            byte[] bytes = exporter.exportToExcel(original);
            DecisionTableDefinition restored = exporter.importFromExcel(bytes);

            assertThat(restored.getTableCode()).isEqualTo("DT_EMPTY_RT");
            assertThat(restored.getRows()).isEmpty();
            assertThat(restored.getDefaultActions()).isNull();
        }

        @Test
        @DisplayName("导出后导入 - 含特殊字符一致")
        void shouldRoundTripSpecialCharacters() {
            DecisionTableDefinition original = DecisionTableDefinition.builder()
                    .tableCode("DT_SPECIAL")
                    .tableName("特殊字符：决策表【测试】")
                    .description("含特殊符号 <>&\"' 测试")
                    .category("测试/分类")
                    .hitPolicy(HitPolicy.COLLECT)
                    .priority(50)
                    .conditionColumns(List.of(
                            DecisionTableDefinition.Column.builder()
                                    .name("字段_中文").label("中文标签【1】").type("string").build()))
                    .actionColumns(List.of(
                            DecisionTableDefinition.Column.builder()
                                    .name("输出").label("输出（结果）").type("string").build()))
                    .rows(List.of(
                            DecisionTableDefinition.Row.builder()
                                    .conditions(Map.of("字段_中文", "值|A|B"))
                                    .actions(Map.of("输出", "结果 < 100 & > 50"))
                                    .build()))
                    .build();

            byte[] bytes = exporter.exportToExcel(original);
            DecisionTableDefinition restored = exporter.importFromExcel(bytes);

            assertThat(restored.getTableCode()).isEqualTo("DT_SPECIAL");
            assertThat(restored.getTableName()).isEqualTo("特殊字符：决策表【测试】");
            assertThat(restored.getDescription()).isEqualTo("含特殊符号 <>&\"' 测试");
            assertThat(restored.getCategory()).isEqualTo("测试/分类");
            assertThat(restored.getConditionColumns().get(0).getName()).isEqualTo("字段_中文");
            assertThat(restored.getConditionColumns().get(0).getLabel()).isEqualTo("中文标签【1】");
            assertThat(restored.getRows()).hasSize(1);
            assertThat(restored.getRows().get(0).getConditions()).containsEntry("字段_中文", "值|A|B");
            assertThat(restored.getRows().get(0).getActions()).containsEntry("输出", "结果 < 100 & > 50");
        }
    }

    // ==================== HitPolicy 测试 ====================

    @Nested
    @DisplayName("各种 HitPolicy 导出/导入")
    class HitPolicyTest {

        @Test
        @DisplayName("FIRST 策略 round-trip")
        void shouldRoundTripFirstHitPolicy() {
            verifyHitPolicyRoundTrip(HitPolicy.FIRST);
        }

        @Test
        @DisplayName("UNIQUE 策略 round-trip")
        void shouldRoundTripUniqueHitPolicy() {
            verifyHitPolicyRoundTrip(HitPolicy.UNIQUE);
        }

        @Test
        @DisplayName("PRIORITY 策略 round-trip")
        void shouldRoundTripPriorityHitPolicy() {
            verifyHitPolicyRoundTrip(HitPolicy.PRIORITY);
        }

        @Test
        @DisplayName("COLLECT 策略 round-trip")
        void shouldRoundTripCollectHitPolicy() {
            verifyHitPolicyRoundTrip(HitPolicy.COLLECT);
        }

        @Test
        @DisplayName("ANY 策略 round-trip")
        void shouldRoundTripAnyHitPolicy() {
            verifyHitPolicyRoundTrip(HitPolicy.ANY);
        }

        @Test
        @DisplayName("RULE_ORDER 策略 round-trip")
        void shouldRoundTripRuleOrderHitPolicy() {
            verifyHitPolicyRoundTrip(HitPolicy.RULE_ORDER);
        }

        private void verifyHitPolicyRoundTrip(HitPolicy policy) {
            DecisionTableDefinition def = DecisionTableDefinition.builder()
                    .tableCode("DT_HP_" + policy.name())
                    .tableName("HitPolicy 测试-" + policy.name())
                    .category("TEST")
                    .hitPolicy(policy)
                    .conditionColumns(List.of(
                            DecisionTableDefinition.Column.builder()
                                    .name("c1").label("c1").type("string").build()))
                    .actionColumns(List.of(
                            DecisionTableDefinition.Column.builder()
                                    .name("a1").label("a1").type("string").build()))
                    .rows(List.of(
                            DecisionTableDefinition.Row.builder()
                                    .conditions(Map.of("c1", "v1"))
                                    .actions(Map.of("a1", "out1"))
                                    .build()))
                    .build();

            byte[] bytes = exporter.exportToExcel(def);
            DecisionTableDefinition restored = exporter.importFromExcel(bytes);

            assertThat(restored.getHitPolicy())
                    .as("HitPolicy %s 应在 round-trip 后保持一致", policy.name())
                    .isEqualTo(policy);
        }
    }

    // ==================== 列类型测试 ====================

    @Nested
    @DisplayName("列类型处理")
    class ColumnTypeTest {

        @Test
        @DisplayName("number 类型 round-trip")
        void shouldRoundTripNumberType() {
            verifyColumnTypeRoundTrip("number");
        }

        @Test
        @DisplayName("string 类型 round-trip")
        void shouldRoundTripStringType() {
            verifyColumnTypeRoundTrip("string");
        }

        @Test
        @DisplayName("boolean 类型 round-trip")
        void shouldRoundTripBooleanType() {
            verifyColumnTypeRoundTrip("boolean");
        }

        private void verifyColumnTypeRoundTrip(String type) {
            DecisionTableDefinition def = DecisionTableDefinition.builder()
                    .tableCode("DT_TYPE_" + type)
                    .tableName("类型测试-" + type)
                    .category("TEST")
                    .hitPolicy(HitPolicy.FIRST)
                    .conditionColumns(List.of(
                            DecisionTableDefinition.Column.builder()
                                    .name("c1").label("条件").type(type).build()))
                    .actionColumns(List.of(
                            DecisionTableDefinition.Column.builder()
                                    .name("a1").label("动作").type(type).build()))
                    .rows(List.of(
                            DecisionTableDefinition.Row.builder()
                                    .conditions(Map.of("c1", "testValue"))
                                    .actions(Map.of("a1", "testResult"))
                                    .build()))
                    .build();

            byte[] bytes = exporter.exportToExcel(def);
            DecisionTableDefinition restored = exporter.importFromExcel(bytes);

            assertThat(restored.getConditionColumns().get(0).getType())
                    .as("条件列类型 %s 应保持一致", type)
                    .isEqualTo(type);
            assertThat(restored.getActionColumns().get(0).getType())
                    .as("动作列类型 %s 应保持一致", type)
                    .isEqualTo(type);
        }
    }

    // ==================== 模板测试 ====================

    @Nested
    @DisplayName("Excel 模板")
    class TemplateTest {

        @Test
        @DisplayName("导出模板 - 返回非空 xlsx")
        void shouldExportTemplate() {
            byte[] bytes = exporter.exportTemplate();

            assertThat(bytes).isNotNull();
            assertThat(bytes.length).isGreaterThan(0);
            assertThat(bytes[0]).isEqualTo((byte) 0x50);
            assertThat(bytes[1]).isEqualTo((byte) 0x4B);
        }

        @Test
        @DisplayName("导出模板后导入 - 可解析为决策表")
        void shouldImportTemplateSuccessfully() {
            byte[] bytes = exporter.exportTemplate();
            DecisionTableDefinition def = exporter.importFromExcel(bytes);

            assertThat(def).isNotNull();
            assertThat(def.getTableCode()).isEqualTo("DT_TEMPLATE");
            assertThat(def.getTableName()).isEqualTo("决策表模板");
            assertThat(def.getConditionColumns()).hasSize(1);
            assertThat(def.getActionColumns()).hasSize(1);
            assertThat(def.getHitPolicy()).isEqualTo(HitPolicy.FIRST);
        }
    }

    // ==================== 边界情况测试 ====================

    @Nested
    @DisplayName("边界情况")
    class EdgeCaseTest {

        @Test
        @DisplayName("导出不含默认动作的决策表 - round-trip 后 defaultActions 为 null")
        void shouldRoundTripTableWithoutDefaultActions() {
            DecisionTableDefinition def = DecisionTableDefinition.builder()
                    .tableCode("DT_NO_DEFAULT")
                    .tableName("无默认动作")
                    .category("TEST")
                    .hitPolicy(HitPolicy.FIRST)
                    .conditionColumns(List.of(
                            DecisionTableDefinition.Column.builder()
                                    .name("c1").label("c1").type("string").build()))
                    .actionColumns(List.of(
                            DecisionTableDefinition.Column.builder()
                                    .name("a1").label("a1").type("string").build()))
                    .rows(List.of(
                            DecisionTableDefinition.Row.builder()
                                    .conditions(Map.of("c1", "v1"))
                                    .actions(Map.of("a1", "out1"))
                                    .build()))
                    .build();

            byte[] bytes = exporter.exportToExcel(def);
            DecisionTableDefinition restored = exporter.importFromExcel(bytes);

            assertThat(restored.getDefaultActions()).isNull();
            assertThat(restored.getRows()).hasSize(1);
        }

        @Test
        @DisplayName("导出含多条件列的决策行 - round-trip 后仅含非空条件")
        void shouldRoundTripRowWithPartialConditions() {
            // 第二行只有 grossMargin 条件，evmRedCount 应为空
            DecisionTableDefinition def = buildFullTable();
            byte[] bytes = exporter.exportToExcel(def);
            DecisionTableDefinition restored = exporter.importFromExcel(bytes);

            DecisionTableDefinition.Row row1 = restored.getRows().get(1);
            assertThat(row1.getConditions()).containsOnlyKeys("grossMargin");
            assertThat(row1.getConditions()).doesNotContainKey("evmRedCount");
        }

        @Test
        @DisplayName("scope 为 null 时 round-trip - 恢复后仍为 null")
        void shouldRoundTripNullScope() {
            DecisionTableDefinition def = DecisionTableDefinition.builder()
                    .tableCode("DT_NULL_SCOPE")
                    .tableName("空Scope")
                    .category("TEST")
                    .hitPolicy(HitPolicy.FIRST)
                    .scope(null)
                    .conditionColumns(List.of(
                            DecisionTableDefinition.Column.builder()
                                    .name("c1").label("c1").type("string").build()))
                    .actionColumns(List.of(
                            DecisionTableDefinition.Column.builder()
                                    .name("a1").label("a1").type("string").build()))
                    .rows(List.of(
                            DecisionTableDefinition.Row.builder()
                                    .conditions(Map.of("c1", "v1"))
                                    .actions(Map.of("a1", "out1"))
                                    .build()))
                    .build();

            byte[] bytes = exporter.exportToExcel(def);
            DecisionTableDefinition restored = exporter.importFromExcel(bytes);

            assertThat(restored.getScope()).isNull();
        }

        @Test
        @DisplayName("含中文表编码 round-trip - 数据一致")
        void shouldRoundTripChineseTableCode() {
            DecisionTableDefinition def = DecisionTableDefinition.builder()
                    .tableCode("DT_项目风险")
                    .tableName("项目风险决策表")
                    .category("风险")
                    .hitPolicy(HitPolicy.FIRST)
                    .conditionColumns(List.of(
                            DecisionTableDefinition.Column.builder()
                                    .name("指标").label("风险指标").type("string").build()))
                    .actionColumns(List.of(
                            DecisionTableDefinition.Column.builder()
                                    .name("等级").label("风险等级").type("string").build()))
                    .rows(List.of(
                            DecisionTableDefinition.Row.builder()
                                    .conditions(Map.of("指标", "高"))
                                    .actions(Map.of("等级", "RED"))
                                    .build()))
                    .build();

            byte[] bytes = exporter.exportToExcel(def);
            DecisionTableDefinition restored = exporter.importFromExcel(bytes);

            assertThat(restored.getTableCode()).isEqualTo("DT_项目风险");
            assertThat(restored.getTableName()).isEqualTo("项目风险决策表");
            assertThat(restored.getCategory()).isEqualTo("风险");
            assertThat(restored.getConditionColumns().get(0).getName()).isEqualTo("指标");
            assertThat(restored.getRows().get(0).getConditions()).containsEntry("指标", "高");
            assertThat(restored.getRows().get(0).getActions()).containsEntry("等级", "RED");
        }
    }
}
