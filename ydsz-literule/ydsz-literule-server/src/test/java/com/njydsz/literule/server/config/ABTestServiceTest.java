package com.njydsz.literule.server.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import com.njydsz.literule.domain.dto.RuleDefinitionDTO;
import com.njydsz.literule.domain.enums.RuleSeverity;
import com.njydsz.literule.domain.expression.ExpressionEngine;
import com.njydsz.literule.server.config.ABTestService.ABTestReport;
import com.njydsz.literule.server.config.ABTestService.Winner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link ABTestService} 单元测试
 *
 * <p>验证 A/B 测试服务的胜者判定逻辑：基于当前规则与候选规则的触发结果（触发/未触发、严重度），
 * 按严重度权重比较并输出结论文案。使用 Mockito 模拟 {@link ExpressionEngine} 的布尔求值结果。
 *
 * @author ydsz-team
 * @since 26.09.16
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class ABTestServiceTest {

  /** 测试用规则编码 */
  private static final String RULE_CODE = "R_BUDGET_CHECK";

  @Mock
  private ExpressionEngine mockEvaluator;

  private ABTestService abTestService;

  @BeforeEach
  void setUp() {
    abTestService = new ABTestService(mockEvaluator);
  }

  /**
   * 创建规则定义 DTO
   *
   * @param code 规则编码
   * @param condition 条件表达式
   * @param severity 默认严重度
   * @return 配置好的 RuleDefinitionDTO
   */
  private RuleDefinitionDTO createDefinition(String code, String condition, RuleSeverity severity) {
    return RuleDefinitionDTO.builder()
        .code(code)
        .name(code + "-name")
        .conditionExpression(condition)
        .defaultSeverity(severity)
        .build();
  }

  /**
   * 创建测试用事实数据
   *
   * @return 包含测试字段的事实 Map
   */
  private Map<String, Object> createFacts() {
    Map<String, Object> facts = new HashMap<>();
    facts.put("amount", 500);
    facts.put("threshold", 100);
    return facts;
  }

  @Nested
  @DisplayName("双方未触发")
  class NeitherTriggered {

    @Test
    @DisplayName("双方均未触发时 Winner 应为 NONE")
    void neitherTriggeredShouldReturnNone() {
      Map<String, Object> facts = createFacts();
      RuleDefinitionDTO current = createDefinition("R_CURRENT", "amount > 1000", RuleSeverity.YELLOW);
      RuleDefinitionDTO candidate = createDefinition("R_CANDIDATE", "amount > 2000", RuleSeverity.RED);

      ABTestReport report = abTestService.test(current, candidate, facts);

      assertThat(report.getWinner()).isEqualTo(Winner.NONE);
      assertThat(report.isCurrentTriggered()).isFalse();
      assertThat(report.isCandidateTriggered()).isFalse();
      assertThat(report.getConclusion()).contains("均未触发");
    }
  }

  @Nested
  @DisplayName("单方触发")
  class SingleTriggered {

    @Test
    @DisplayName("仅候选规则触发时 Winner 应为 CANDIDATE")
    void onlyCandidateTriggeredShouldReturnCandidate() {
      Map<String, Object> facts = createFacts();
      RuleDefinitionDTO current = createDefinition("R_CURRENT", "amount > 1000", RuleSeverity.YELLOW);
      RuleDefinitionDTO candidate = createDefinition("R_CANDIDATE", "amount > 100", RuleSeverity.RED);

      ABTestReport report = abTestService.test(current, candidate, facts);

      assertThat(report.getWinner()).isEqualTo(Winner.CANDIDATE);
      assertThat(report.isCandidateTriggered()).isTrue();
      assertThat(report.isCurrentTriggered()).isFalse();
    }

    @Test
    @DisplayName("仅当前规则触发时 Winner 应为 CURRENT")
    void onlyCurrentTriggeredShouldReturnCurrent() {
      Map<String, Object> facts = createFacts();
      RuleDefinitionDTO current = createDefinition("R_CURRENT", "amount > 100", RuleSeverity.YELLOW);
      RuleDefinitionDTO candidate = createDefinition("R_CANDIDATE", "amount > 1000", RuleSeverity.RED);

      ABTestReport report = abTestService.test(current, candidate, facts);

      assertThat(report.getWinner()).isEqualTo(Winner.CURRENT);
      assertThat(report.isCurrentTriggered()).isTrue();
      assertThat(report.isCandidateTriggered()).isFalse();
    }
  }

  @Nested
  @DisplayName("双方均触发")
  class BothTriggered {

    @Test
    @DisplayName("候选规则严重度更高时 Winner 应为 CANDIDATE")
    void candidateHigherSeverityShouldReturnCandidate() {
      Map<String, Object> facts = createFacts();
      RuleDefinitionDTO current = createDefinition("R_CURRENT", "amount > 100", RuleSeverity.YELLOW);
      RuleDefinitionDTO candidate = createDefinition("R_CANDIDATE", "amount > 50", RuleSeverity.RED);

      ABTestReport report = abTestService.test(current, candidate, facts);

      assertThat(report.isCurrentTriggered()).isTrue();
      assertThat(report.isCandidateTriggered()).isTrue();
      assertThat(report.getCurrentSeverity()).isEqualTo(RuleSeverity.YELLOW);
      assertThat(report.getCandidateSeverity()).isEqualTo(RuleSeverity.RED);
      assertThat(report.getWinner()).isEqualTo(Winner.CANDIDATE);
      assertThat(report.getConclusion()).contains("候选规则");
    }

    @Test
    @DisplayName("当前规则严重度更高时 Winner 应为 CURRENT")
    void currentHigherSeverityShouldReturnCurrent() {
      Map<String, Object> facts = createFacts();
      RuleDefinitionDTO current = createDefinition("R_CURRENT", "amount > 100", RuleSeverity.RED);
      RuleDefinitionDTO candidate = createDefinition("R_CANDIDATE", "amount > 50", RuleSeverity.YELLOW);

      ABTestReport report = abTestService.test(current, candidate, facts);

      assertThat(report.getWinner()).isEqualTo(Winner.CURRENT);
      assertThat(report.getConclusion()).contains("当前规则");
    }

    @Test
    @DisplayName("双方严重度相同时 Winner 应为 TIE")
    void equalSeverityShouldReturnTie() {
      Map<String, Object> facts = createFacts();
      RuleDefinitionDTO current = createDefinition("R_CURRENT", "amount > 100", RuleSeverity.RED);
      RuleDefinitionDTO candidate = createDefinition("R_CANDIDATE", "amount > 50", RuleSeverity.RED);

      ABTestReport report = abTestService.test(current, candidate, facts);

      assertThat(report.getWinner()).isEqualTo(Winner.TIE);
      assertThat(report.getConclusion()).contains("一致");
    }
  }

  @Nested
  @DisplayName("报告字段")
  class ReportFields {

    @Test
    @DisplayName("报告应包含规则编码和评估时间")
    void reportShouldContainRuleCodeAndTimestamp() {
      Map<String, Object> facts = createFacts();
      RuleDefinitionDTO current = createDefinition(RULE_CODE, "amount > 100", RuleSeverity.RED);
      RuleDefinitionDTO candidate = createDefinition("R_CANDIDATE", "amount > 50", RuleSeverity.RED);

      ABTestReport report = abTestService.test(current, candidate, facts);

      assertThat(report.getRuleCode()).isEqualTo(RULE_CODE);
      assertThat(report.getEvaluatedAt()).isNotNull();
      assertThat(report.getEvaluatedAt()).isBeforeOrEqualTo(LocalDateTime.now().plusSeconds(1));
    }
  }
}
