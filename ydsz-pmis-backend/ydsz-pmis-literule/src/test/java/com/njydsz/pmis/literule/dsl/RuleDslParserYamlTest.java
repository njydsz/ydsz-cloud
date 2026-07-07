package com.njydsz.pmis.literule.dsl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RuleDslParser YAML/JSON/文件加载单元测试（P2-3）
 *
 * <p>覆盖 {@link RuleDslParser} 新增的 {@code parseYaml} / {@code parseJson} /
 * {@code loadFromFile} / {@code loadFromStream} 四个方法，验证：
 * <ul>
 *   <li>YAML 字符串解析为 DSL 模型（含 rules / chains / meta 段）</li>
 *   <li>JSON 字符串解析为 DSL 模型（字段名与 YAML 一致，使用 snake_case）</li>
 *   <li>从 .yml / .yaml / .json 文件加载，按后缀自动选择解析器</li>
 *   <li>不支持的后缀抛出 IllegalArgumentException</li>
 *   <li>从 InputStream 按指定格式加载</li>
 *   <li>空内容 / null 输入的容错处理</li>
 * </ul>
 *
 * <p>测试风格参考 {@code DefaultRuleEngineTest}：JUnit 5 + AssertJ。
 *
 * @author ydsz-pmis-team
 * @since 1.7.0
 */
@DisplayName("RuleDslParser YAML/JSON/文件加载（P2-3）")
class RuleDslParserYamlTest {

    @TempDir
    Path tempDir;

    // ==================== 测试夹具 ====================

    /**
     * 标准表达式规则 YAML（含 rules / chains / meta 三段）
     */
    private static final String YAML_CONTENT = """
            meta:
              version: 1.7.0
              description: 测试规则集
            rules:
              - code: EVM_RED_ALERT
                name: EVM红灯告警
                type: expression
                category: EVM
                category_path: finance/evm
                priority: 10
                severity: RED
                condition: "evmRedCount >= 3"
                title: "EVM 红灯 ${evmRedCount} 个"
                mutex_group: EVM_ALERTS
              - code: COST_OVERRUN
                name: 成本超支告警
                type: expression
                category: COST
                priority: 50
                severity: YELLOW
                condition: "cost > budget"
            chains:
              - name: RISK_CHAIN
                type: THEN
                steps: [EVM_RED_ALERT, COST_OVERRUN]
            """;

    /**
     * 等价的 JSON 内容（字段名使用 snake_case，与 YAML 一致）
     */
    private static final String JSON_CONTENT = """
            {
              "meta": {
                "version": "1.7.0",
                "description": "测试规则集"
              },
              "rules": [
                {
                  "code": "EVM_RED_ALERT",
                  "name": "EVM红灯告警",
                  "type": "expression",
                  "category": "EVM",
                  "category_path": "finance/evm",
                  "priority": 10,
                  "severity": "RED",
                  "condition": "evmRedCount >= 3",
                  "title": "EVM 红灯 ${evmRedCount} 个",
                  "mutex_group": "EVM_ALERTS"
                },
                {
                  "code": "COST_OVERRUN",
                  "name": "成本超支告警",
                  "type": "expression",
                  "category": "COST",
                  "priority": 50,
                  "severity": "YELLOW",
                  "condition": "cost > budget"
                }
              ],
              "chains": [
                {
                  "name": "RISK_CHAIN",
                  "type": "THEN",
                  "steps": ["EVM_RED_ALERT", "COST_OVERRUN"]
                }
              ]
            }
            """;

    // ==================== parseYaml 测试 ====================

    @Test
    @DisplayName("parseYaml - 解析完整 YAML 为 DSL 模型")
    void shouldParseYamlToDsl() {
        RuleDsl dsl = RuleDslParser.parseYaml(YAML_CONTENT);

        assertThat(dsl).isNotNull();
        // rules 段
        assertThat(dsl.getRules()).hasSize(2);
        RuleDslEntry evmRule = dsl.getRules().get(0);
        assertThat(evmRule.getCode()).isEqualTo("EVM_RED_ALERT");
        assertThat(evmRule.getName()).isEqualTo("EVM红灯告警");
        assertThat(evmRule.getType()).isEqualTo("expression");
        assertThat(evmRule.getCategory()).isEqualTo("EVM");
        assertThat(evmRule.getCategoryPath()).isEqualTo("finance/evm");
        assertThat(evmRule.getPriority()).isEqualTo(10);
        assertThat(evmRule.getSeverity()).isEqualTo("RED");
        assertThat(evmRule.getCondition()).isEqualTo("evmRedCount >= 3");
        assertThat(evmRule.getMutexGroup()).isEqualTo("EVM_ALERTS");

        // chains 段
        assertThat(dsl.getChains()).hasSize(1);
        ChainDslEntry chain = dsl.getChains().get(0);
        assertThat(chain.getName()).isEqualTo("RISK_CHAIN");
        assertThat(chain.getType()).isEqualTo("THEN");
        assertThat(chain.getSteps()).containsExactly("EVM_RED_ALERT", "COST_OVERRUN");

        // meta 段
        assertThat(dsl.getMeta()).isNotNull();
        assertThat(dsl.getMeta().get("version")).isEqualTo("1.7.0");
    }

    @Test
    @DisplayName("parseYaml - 空内容返回空 DSL")
    void shouldReturnEmptyDslForBlankYaml() {
        RuleDsl dsl = RuleDslParser.parseYaml("");
        assertThat(dsl).isNotNull();
        assertThat(dsl.getRules()).isEmpty();
        assertThat(dsl.getChains()).isEmpty();
    }

    @Test
    @DisplayName("parseYaml - null 返回空 DSL")
    void shouldReturnEmptyDslForNullYaml() {
        RuleDsl dsl = RuleDslParser.parseYaml(null);
        assertThat(dsl).isNotNull();
        assertThat(dsl.getRules()).isEmpty();
    }

    @Test
    @DisplayName("parseYaml - 与 parse 方法行为一致（语义化别名）")
    void shouldBehaveSameAsParse() {
        RuleDsl byParseYaml = RuleDslParser.parseYaml(YAML_CONTENT);
        RuleDsl byParse = RuleDslParser.parse(YAML_CONTENT);

        assertThat(byParseYaml.getRules()).hasSameSizeAs(byParse.getRules());
        assertThat(byParseYaml.getRules().get(0).getCode())
                .isEqualTo(byParse.getRules().get(0).getCode());
    }

    // ==================== parseJson 测试 ====================

    @Test
    @DisplayName("parseJson - 解析完整 JSON 为 DSL 模型")
    void shouldParseJsonToDsl() {
        RuleDsl dsl = RuleDslParser.parseJson(JSON_CONTENT);

        assertThat(dsl).isNotNull();
        // rules 段
        assertThat(dsl.getRules()).hasSize(2);
        RuleDslEntry evmRule = dsl.getRules().get(0);
        assertThat(evmRule.getCode()).isEqualTo("EVM_RED_ALERT");
        assertThat(evmRule.getName()).isEqualTo("EVM红灯告警");
        assertThat(evmRule.getType()).isEqualTo("expression");
        assertThat(evmRule.getCategory()).isEqualTo("EVM");
        assertThat(evmRule.getCategoryPath()).isEqualTo("finance/evm");
        assertThat(evmRule.getPriority()).isEqualTo(10);
        assertThat(evmRule.getSeverity()).isEqualTo("RED");
        assertThat(evmRule.getCondition()).isEqualTo("evmRedCount >= 3");
        assertThat(evmRule.getMutexGroup()).isEqualTo("EVM_ALERTS");

        // chains 段
        assertThat(dsl.getChains()).hasSize(1);
        ChainDslEntry chain = dsl.getChains().get(0);
        assertThat(chain.getName()).isEqualTo("RISK_CHAIN");
        assertThat(chain.getType()).isEqualTo("THEN");
        assertThat(chain.getSteps()).containsExactly("EVM_RED_ALERT", "COST_OVERRUN");

        // meta 段
        assertThat(dsl.getMeta()).isNotNull();
        assertThat(dsl.getMeta().get("version")).isEqualTo("1.7.0");
    }

    @Test
    @DisplayName("parseJson - YAML 与 JSON 解析结果等价")
    void shouldProduceEquivalentResultsForYamlAndJson() {
        RuleDsl yamlDsl = RuleDslParser.parseYaml(YAML_CONTENT);
        RuleDsl jsonDsl = RuleDslParser.parseJson(JSON_CONTENT);

        // 规则数量一致
        assertThat(jsonDsl.getRules()).hasSameSizeAs(yamlDsl.getRules());
        // 第一条规则的字段值一致
        RuleDslEntry yamlRule = yamlDsl.getRules().get(0);
        RuleDslEntry jsonRule = jsonDsl.getRules().get(0);
        assertThat(jsonRule.getCode()).isEqualTo(yamlRule.getCode());
        assertThat(jsonRule.getName()).isEqualTo(yamlRule.getName());
        assertThat(jsonRule.getCondition()).isEqualTo(yamlRule.getCondition());
        assertThat(jsonRule.getSeverity()).isEqualTo(yamlRule.getSeverity());
        assertThat(jsonRule.getPriority()).isEqualTo(yamlRule.getPriority());
        // 链一致
        assertThat(jsonDsl.getChains()).hasSameSizeAs(yamlDsl.getChains());
        assertThat(jsonDsl.getChains().get(0).getSteps())
                .isEqualTo(yamlDsl.getChains().get(0).getSteps());
    }

    @Test
    @DisplayName("parseJson - 空内容返回空 DSL")
    void shouldReturnEmptyDslForBlankJson() {
        RuleDsl dsl = RuleDslParser.parseJson("");
        assertThat(dsl).isNotNull();
        assertThat(dsl.getRules()).isEmpty();
    }

    @Test
    @DisplayName("parseJson - null 返回空 DSL")
    void shouldReturnEmptyDslForNullJson() {
        RuleDsl dsl = RuleDslParser.parseJson(null);
        assertThat(dsl).isNotNull();
        assertThat(dsl.getRules()).isEmpty();
    }

    // ==================== loadFromFile 测试 ====================

    @Test
    @DisplayName("loadFromFile - 加载 .yml 文件")
    void shouldLoadYmlFile() throws IOException {
        Path file = tempDir.resolve("rules.yml");
        Files.writeString(file, YAML_CONTENT);

        RuleDsl dsl = RuleDslParser.loadFromFile(file);

        assertThat(dsl).isNotNull();
        assertThat(dsl.getRules()).hasSize(2);
        assertThat(dsl.getRules().get(0).getCode()).isEqualTo("EVM_RED_ALERT");
    }

    @Test
    @DisplayName("loadFromFile - 加载 .yaml 文件")
    void shouldLoadYamlFile() throws IOException {
        Path file = tempDir.resolve("rules.yaml");
        Files.writeString(file, YAML_CONTENT);

        RuleDsl dsl = RuleDslParser.loadFromFile(file);

        assertThat(dsl).isNotNull();
        assertThat(dsl.getRules()).hasSize(2);
        assertThat(dsl.getRules().get(0).getCode()).isEqualTo("EVM_RED_ALERT");
    }

    @Test
    @DisplayName("loadFromFile - 加载 .json 文件")
    void shouldLoadJsonFile() throws IOException {
        Path file = tempDir.resolve("rules.json");
        Files.writeString(file, JSON_CONTENT);

        RuleDsl dsl = RuleDslParser.loadFromFile(file);

        assertThat(dsl).isNotNull();
        assertThat(dsl.getRules()).hasSize(2);
        assertThat(dsl.getRules().get(0).getCode()).isEqualTo("EVM_RED_ALERT");
        assertThat(dsl.getRules().get(1).getCode()).isEqualTo("COST_OVERRUN");
    }

    @Test
    @DisplayName("loadFromFile - 不支持的后缀抛出 IllegalArgumentException")
    void shouldThrowForUnsupportedExtension() throws IOException {
        Path file = tempDir.resolve("rules.txt");
        Files.writeString(file, "some content");

        assertThatThrownBy(() -> RuleDslParser.loadFromFile(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的规则文件后缀");
    }

    @Test
    @DisplayName("loadFromFile - null 路径抛出 IllegalArgumentException")
    void shouldThrowForNullPath() {
        assertThatThrownBy(() -> RuleDslParser.loadFromFile(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("文件路径不能为 null");
    }

    @Test
    @DisplayName("loadFromFile - .yml 与 .json 解析结果等价")
    void shouldProduceEquivalentResultsForYmlAndJsonFiles() throws IOException {
        Path ymlFile = tempDir.resolve("rules.yml");
        Files.writeString(ymlFile, YAML_CONTENT);
        Path jsonFile = tempDir.resolve("rules.json");
        Files.writeString(jsonFile, JSON_CONTENT);

        RuleDsl ymlDsl = RuleDslParser.loadFromFile(ymlFile);
        RuleDsl jsonDsl = RuleDslParser.loadFromFile(jsonFile);

        assertThat(jsonDsl.getRules()).hasSameSizeAs(ymlDsl.getRules());
        assertThat(jsonDsl.getRules().get(0).getCode())
                .isEqualTo(ymlDsl.getRules().get(0).getCode());
        assertThat(jsonDsl.getRules().get(0).getCondition())
                .isEqualTo(ymlDsl.getRules().get(0).getCondition());
    }

    // ==================== loadFromStream 测试 ====================

    @Test
    @DisplayName("loadFromStream - 按 yaml 格式从 InputStream 加载")
    void shouldLoadFromStreamAsYaml() throws IOException {
        ByteArrayInputStream stream = new ByteArrayInputStream(YAML_CONTENT.getBytes());

        RuleDsl dsl = RuleDslParser.loadFromStream(stream, "yaml");

        assertThat(dsl).isNotNull();
        assertThat(dsl.getRules()).hasSize(2);
        assertThat(dsl.getRules().get(0).getCode()).isEqualTo("EVM_RED_ALERT");
    }

    @Test
    @DisplayName("loadFromStream - 按 json 格式从 InputStream 加载")
    void shouldLoadFromStreamAsJson() throws IOException {
        ByteArrayInputStream stream = new ByteArrayInputStream(JSON_CONTENT.getBytes());

        RuleDsl dsl = RuleDslParser.loadFromStream(stream, "json");

        assertThat(dsl).isNotNull();
        assertThat(dsl.getRules()).hasSize(2);
        assertThat(dsl.getRules().get(0).getCode()).isEqualTo("EVM_RED_ALERT");
    }

    @Test
    @DisplayName("loadFromStream - yml 格式别名等价于 yaml")
    void shouldTreatYmlAsYaml() throws IOException {
        ByteArrayInputStream stream = new ByteArrayInputStream(YAML_CONTENT.getBytes());

        RuleDsl dsl = RuleDslParser.loadFromStream(stream, "yml");

        assertThat(dsl.getRules()).hasSize(2);
    }

    @Test
    @DisplayName("loadFromStream - 大小写不敏感")
    void shouldIgnoreCaseInFormat() throws IOException {
        ByteArrayInputStream stream = new ByteArrayInputStream(YAML_CONTENT.getBytes());

        RuleDsl dsl = RuleDslParser.loadFromStream(stream, "YAML");

        assertThat(dsl.getRules()).hasSize(2);
    }

    @Test
    @DisplayName("loadFromStream - 不支持的格式抛出 IllegalArgumentException")
    void shouldThrowForUnsupportedFormat() {
        ByteArrayInputStream stream = new ByteArrayInputStream("{}".getBytes());

        assertThatThrownBy(() -> RuleDslParser.loadFromStream(stream, "xml"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的规则文件格式");
    }

    @Test
    @DisplayName("loadFromStream - null 流返回空 DSL")
    void shouldReturnEmptyForNullStream() throws IOException {
        RuleDsl dsl = RuleDslParser.loadFromStream(null, "yaml");
        assertThat(dsl).isNotNull();
        assertThat(dsl.getRules()).isEmpty();
    }

    @Test
    @DisplayName("loadFromStream - 空 format 抛出 IllegalArgumentException")
    void shouldThrowForBlankFormat() {
        ByteArrayInputStream stream = new ByteArrayInputStream("{}".getBytes());

        assertThatThrownBy(() -> RuleDslParser.loadFromStream(stream, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("format 不能为空");
    }

    // ==================== validate 联动测试 ====================

    @Test
    @DisplayName("parseYaml + validate - 解析后可校验通过")
    void shouldPassValidationAfterYamlParse() {
        RuleDsl dsl = RuleDslParser.parseYaml(YAML_CONTENT);
        // 不抛异常即通过
        RuleDslParser.validate(dsl);
    }

    @Test
    @DisplayName("parseJson + validate - 解析后可校验通过")
    void shouldPassValidationAfterJsonParse() {
        RuleDsl dsl = RuleDslParser.parseJson(JSON_CONTENT);
        // 不抛异常即通过
        RuleDslParser.validate(dsl);
    }
}
